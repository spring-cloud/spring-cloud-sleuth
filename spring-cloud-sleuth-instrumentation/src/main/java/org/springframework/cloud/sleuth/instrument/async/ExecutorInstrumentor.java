/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.async;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Supplier;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ReflectionUtils;

/**
 * Wraps {@link Executor}s in tracing representations.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class ExecutorInstrumentor {

	private static final Log log = LogFactory.getLog(ExecutorInstrumentor.class);

	private final Supplier<List<String>> ignoredBeans;

	private final BeanFactory beanFactory;

	public ExecutorInstrumentor(Supplier<List<String>> ignoredBeans, BeanFactory beanFactory) {
		this.ignoredBeans = ignoredBeans;
		this.beanFactory = beanFactory;
	}

	/**
	 * @param bean bean to instrument
	 * @return {@code true} if bean is applicable for instrumentation
	 */
	public static boolean isApplicableForInstrumentation(Object bean) {
		return bean instanceof Executor && !(bean instanceof LazyTraceThreadPoolTaskExecutor
				|| bean instanceof TraceableScheduledExecutorService || bean instanceof TraceableExecutorService
				|| bean instanceof LazyTraceAsyncTaskExecutor || bean instanceof LazyTraceExecutor);
	}

	/**
	 * Wraps an {@link Executor} bean in its trace representation.
	 * @param bean a bean (might be of {@link Executor} type
	 * @param beanName name of the bean
	 * @return wrapped bean or just bean if not {@link Executor} or already instrumented
	 */
	public Object instrument(Object bean, String beanName) {
		if (!isApplicableForInstrumentation(bean)) {
			log.info("Bean is already instrumented or is not applicable for instrumentation " + beanName);
			return bean;
		}
		if (bean instanceof ThreadPoolTaskExecutor) {
			if (isProxyNeeded(beanName)) {
				return wrapThreadPoolTaskExecutor(bean, beanName);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		else if (bean instanceof ScheduledExecutorService) {
			if (isProxyNeeded(beanName)) {
				return wrapScheduledExecutorService(bean, beanName);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		else if (bean instanceof ExecutorService) {
			if (isProxyNeeded(beanName)) {
				return wrapExecutorService(bean, beanName);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		else if (bean instanceof AsyncTaskExecutor) {
			if (isProxyNeeded(beanName)) {
				return wrapAsyncTaskExecutor(bean, beanName);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		else if (bean instanceof Executor) {
			return wrapExecutor(bean, beanName);
		}
		return bean;
	}

	private Object wrapExecutor(Object bean, String beanName) {
		Executor executor = (Executor) bean;
		boolean methodFinal = anyFinalMethods(executor);
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean cglibProxy = !methodFinal && !classFinal;
		try {
			return createProxy(bean, cglibProxy, new ExecutorMethodInterceptor<>(executor, this.beanFactory, beanName));
		}
		catch (AopConfigException ex) {
			if (cglibProxy) {
				if (log.isDebugEnabled()) {
					log.debug("Exception occurred while trying to create a proxy, falling back to JDK proxy", ex);
				}
				return createProxy(bean, false, new ExecutorMethodInterceptor<>(executor, this.beanFactory, beanName));
			}
			throw ex;
		}
	}

	private Object wrapThreadPoolTaskExecutor(Object bean, String beanName) {
		ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) bean;
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean methodsFinal = anyFinalMethods(executor);
		boolean cglibProxy = !classFinal && !methodsFinal;
		return createThreadPoolTaskExecutorProxy(bean, cglibProxy, executor, beanName);
	}

	private Object wrapExecutorService(Object bean, String beanName) {
		ExecutorService executor = (ExecutorService) bean;
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean methodFinal = anyFinalMethods(executor);
		boolean cglibProxy = !classFinal && !methodFinal;
		return createExecutorServiceProxy(bean, cglibProxy, executor, beanName);
	}

	private Object wrapScheduledExecutorService(Object bean, String beanName) {
		ScheduledExecutorService executor = (ScheduledExecutorService) bean;
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean methodFinal = anyFinalMethods(executor);
		boolean cglibProxy = !classFinal && !methodFinal;
		return createScheduledExecutorServiceProxy(bean, cglibProxy, executor, beanName);
	}

	private Object wrapAsyncTaskExecutor(Object bean, String beanName) {
		AsyncTaskExecutor executor = (AsyncTaskExecutor) bean;
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean methodsFinal = anyFinalMethods(executor);
		boolean cglibProxy = !classFinal && !methodsFinal;
		return createAsyncTaskExecutorProxy(bean, cglibProxy, executor, beanName);
	}

	boolean isProxyNeeded(String beanName) {
		return !this.ignoredBeans.get().contains(beanName);
	}

	Object createThreadPoolTaskExecutorProxy(Object bean, boolean cglibProxy, ThreadPoolTaskExecutor executor,
			String beanName) {
		if (!cglibProxy) {
			return new LazyTraceThreadPoolTaskExecutor(this.beanFactory, executor, beanName);
		}
		return getProxiedObject(bean, beanName, true, executor,
				() -> new LazyTraceThreadPoolTaskExecutor(this.beanFactory, executor, beanName));
	}

	Supplier<Executor> createThreadPoolTaskSchedulerProxy(ThreadPoolTaskScheduler executor, String beanName) {
		return () -> new LazyTraceThreadPoolTaskScheduler(this.beanFactory, executor, beanName);
	}

	Supplier<Executor> createScheduledThreadPoolExecutorProxy(ScheduledThreadPoolExecutor executor, String beanName) {
		return () -> new LazyTraceScheduledThreadPoolExecutor(executor.getCorePoolSize(), executor.getThreadFactory(),
				executor.getRejectedExecutionHandler(), this.beanFactory, executor, beanName);
	}

	Object createExecutorServiceProxy(Object bean, boolean cglibProxy, ExecutorService executor, String beanName) {
		return getProxiedObject(bean, beanName, cglibProxy, executor, () -> {
			if (executor instanceof ScheduledExecutorService) {
				return new TraceableScheduledExecutorService(this.beanFactory, executor, beanName);
			}
			return new TraceableExecutorService(this.beanFactory, executor, beanName);
		});
	}

	Object createScheduledExecutorServiceProxy(Object bean, boolean cglibProxy, ScheduledExecutorService executor,
			String beanName) {
		return getProxiedObject(bean, beanName, cglibProxy, executor,
				() -> new TraceableScheduledExecutorService(this.beanFactory, executor, beanName));
	}

	Object createAsyncTaskExecutorProxy(Object bean, boolean cglibProxy, AsyncTaskExecutor executor, String beanName) {
		return getProxiedObject(bean, beanName, cglibProxy, executor, () -> {
			if (bean instanceof ThreadPoolTaskScheduler) {
				return new LazyTraceThreadPoolTaskScheduler(this.beanFactory, (ThreadPoolTaskScheduler) executor,
						beanName);
			}
			return new LazyTraceAsyncTaskExecutor(this.beanFactory, executor, beanName);
		});
	}

	private Object getProxiedObject(Object bean, String beanName, boolean cglibProxy, Executor executor,
			Supplier<Executor> supplier) {
		ProxyFactoryBean factory = proxyFactoryBean(bean, beanName, cglibProxy, executor, supplier);
		try {
			return getObject(factory);
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("Exception occurred while trying to get a proxy. Will fallback to a different implementation",
						ex);
			}
			try {
				if (bean instanceof ThreadPoolTaskScheduler) {
					if (log.isDebugEnabled()) {
						log.debug(
								"Will wrap ThreadPoolTaskScheduler in its tracing representation due to previous errors");
					}
					return createThreadPoolTaskSchedulerProxy((ThreadPoolTaskScheduler) bean, beanName).get();
				}
				else if (bean instanceof ScheduledThreadPoolExecutor) {
					if (log.isDebugEnabled()) {
						log.debug(
								"Will wrap ScheduledThreadPoolExecutor in its tracing representation due to previous errors");
					}
					return createScheduledThreadPoolExecutorProxy((ScheduledThreadPoolExecutor) bean, beanName).get();
				}
			}
			catch (Exception ex2) {
				if (log.isDebugEnabled()) {
					log.debug("Fallback for special wrappers failed, will try the tracing representation instead", ex2);
				}
			}
			return supplier.get();
		}
	}

	private ProxyFactoryBean proxyFactoryBean(Object bean, String beanName, boolean cglibProxy, Executor executor,
			Supplier<Executor> supplier) {
		ProxyFactoryBean factory = new ProxyFactoryBean();
		factory.setProxyTargetClass(cglibProxy);
		factory.addAdvice(new ExecutorMethodInterceptor<Executor>(executor, this.beanFactory, beanName) {
			@Override
			Executor executor(BeanFactory beanFactory, Executor executor, String beanName) {
				return supplier.get();
			}
		});
		factory.setTarget(bean);
		return factory;
	}

	Object getObject(ProxyFactoryBean factory) {
		return factory.getObject();
	}

	@SuppressWarnings("unchecked")
	Object createProxy(Object bean, boolean cglibProxy, Advice advice) {
		ProxyFactoryBean factory = new ProxyFactoryBean();
		factory.setProxyTargetClass(cglibProxy);
		factory.addAdvice(advice);
		factory.setTarget(bean);
		return getObject(factory);
	}

	private static <T> boolean anyFinalMethods(T object) {
		try {
			for (Method method : ReflectionUtils.getAllDeclaredMethods(object.getClass())) {
				if (method.getDeclaringClass().equals(Object.class)) {
					continue;
				}
				Method m = ReflectionUtils.findMethod(object.getClass(), method.getName(), method.getParameterTypes());
				if (m != null && Modifier.isPublic(m.getModifiers()) && Modifier.isFinal(m.getModifiers())) {
					return true;
				}
			}
		}
		catch (IllegalAccessError er) {
			if (log.isDebugEnabled()) {
				log.debug("Error occurred while trying to access methods", er);
			}
			return false;
		}
		return false;
	}

}

/**
 * Interceptor for executor methods.
 *
 * @param <T> - executor type
 * @author Marcin Grzejszczak
 */
class ExecutorMethodInterceptor<T extends Executor> implements MethodInterceptor {

	private final T delegate;

	private final BeanFactory beanFactory;

	private final String beanName;

	ExecutorMethodInterceptor(T delegate, BeanFactory beanFactory, String beanName) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
		this.beanName = beanName;
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		T executor = executor(this.beanFactory, this.delegate, this.beanName);
		Method methodOnTracedBean = getMethod(invocation, executor);
		if (methodOnTracedBean != null) {
			try {
				return methodOnTracedBean.invoke(executor, invocation.getArguments());
			}
			catch (InvocationTargetException ex) {
				// gh-1092: throw the target exception (if present)
				Throwable cause = ex.getCause();
				throw (cause != null) ? cause : ex;
			}
		}
		return invocation.proceed();
	}

	private Method getMethod(MethodInvocation invocation, Object object) {
		Method method = invocation.getMethod();
		return ReflectionUtils.findMethod(object.getClass(), method.getName(), method.getParameterTypes());
	}

	T executor(BeanFactory beanFactory, T executor, String beanName) {
		return (T) new LazyTraceExecutor(beanFactory, executor, beanName);
	}

}
