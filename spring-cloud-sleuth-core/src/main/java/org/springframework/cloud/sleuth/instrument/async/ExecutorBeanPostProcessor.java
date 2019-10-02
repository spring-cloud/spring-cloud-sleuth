/*
 * Copyright 2013-2019 the original author or authors.
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
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ReflectionUtils;

/**
 * Bean post processor that wraps a call to an {@link Executor} either in a JDK or CGLIB
 * proxy. Depending on whether the implementation has a final method or is final.
 *
 * @author Marcin Grzejszczak
 * @author Jesus Alonso
 * @author Denys Ivano
 * @author Vladislav Fefelov
 * @since 1.1.4
 */
class ExecutorBeanPostProcessor implements BeanPostProcessor {

	private static final Log log = LogFactory.getLog(ExecutorBeanPostProcessor.class);

	private final BeanFactory beanFactory;

	private SleuthAsyncProperties sleuthAsyncProperties;

	ExecutorBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof LazyTraceThreadPoolTaskExecutor
				|| bean instanceof TraceableExecutorService
				|| bean instanceof LazyTraceAsyncTaskExecutor
				|| bean instanceof LazyTraceExecutor) {
			log.info("Bean is already instrumented " + beanName);
			return bean;
		}
		if (bean instanceof ThreadPoolTaskExecutor) {
			if (isProxyNeeded(beanName)) {
				return wrapThreadPoolTaskExecutor(bean);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		else if (bean instanceof ExecutorService) {
			if (isProxyNeeded(beanName)) {
				return wrapExecutorService(bean);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		else if (bean instanceof AsyncTaskExecutor) {
			if (isProxyNeeded(beanName)) {
				return wrapAsyncTaskExecutor(bean);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		else if (bean instanceof Executor) {
			return wrapExecutor(bean);
		}
		return bean;
	}

	private Object wrapExecutor(Object bean) {
		Executor executor = (Executor) bean;
		boolean methodFinal = anyFinalMethods(executor, Executor.class);
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean cglibProxy = !methodFinal && !classFinal;
		try {
			return createProxy(bean, cglibProxy,
					new ExecutorMethodInterceptor<>(executor, this.beanFactory));
		}
		catch (AopConfigException ex) {
			if (cglibProxy) {
				if (log.isDebugEnabled()) {
					log.debug(
							"Exception occurred while trying to create a proxy, falling back to JDK proxy",
							ex);
				}
				return createProxy(bean, false,
						new ExecutorMethodInterceptor<>(executor, this.beanFactory));
			}
			throw ex;
		}
	}

	private Object wrapThreadPoolTaskExecutor(Object bean) {
		ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) bean;
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean methodsFinal = anyFinalMethods(executor, ThreadPoolTaskExecutor.class);
		boolean cglibProxy = !classFinal && !methodsFinal;
		return createThreadPoolTaskExecutorProxy(bean, cglibProxy, executor);
	}

	private Object wrapExecutorService(Object bean) {
		ExecutorService executor = (ExecutorService) bean;
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean methodFinal = anyFinalMethods(executor, ExecutorService.class);
		boolean cglibProxy = !classFinal && !methodFinal;
		return createExecutorServiceProxy(bean, cglibProxy, executor);
	}

	private Object wrapAsyncTaskExecutor(Object bean) {
		AsyncTaskExecutor executor = (AsyncTaskExecutor) bean;
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean methodsFinal = anyFinalMethods(executor, AsyncTaskExecutor.class);
		boolean cglibProxy = !classFinal && !methodsFinal;
		return createAsyncTaskExecutorProxy(bean, cglibProxy, executor);
	}

	boolean isProxyNeeded(String beanName) {
		SleuthAsyncProperties sleuthAsyncProperties = asyncConfigurationProperties();
		return !sleuthAsyncProperties.getIgnoredBeans().contains(beanName);
	}

	Object createThreadPoolTaskExecutorProxy(Object bean, boolean cglibProxy,
			ThreadPoolTaskExecutor executor) {
		if (!cglibProxy) {
			return new LazyTraceThreadPoolTaskExecutor(this.beanFactory, executor);
		}
		return getProxiedObject(bean, cglibProxy, executor,
				() -> new LazyTraceThreadPoolTaskExecutor(this.beanFactory, executor));
	}

	Supplier<Executor> createThreadPoolTaskSchedulerProxy(
			ThreadPoolTaskScheduler executor) {
		return () -> new LazyTraceThreadPoolTaskScheduler(this.beanFactory, executor);
	}

	Supplier<Executor> createScheduledThreadPoolExecutorProxy(
			ScheduledThreadPoolExecutor executor) {
		return () -> new LazyTraceScheduledThreadPoolExecutor(executor.getCorePoolSize(),
				executor.getThreadFactory(), executor.getRejectedExecutionHandler(),
				this.beanFactory, executor);
	}

	Object createExecutorServiceProxy(Object bean, boolean cglibProxy,
			ExecutorService executor) {
		return getProxiedObject(bean, cglibProxy, executor, () -> {
			if (executor instanceof ScheduledExecutorService) {
				return new TraceableScheduledExecutorService(this.beanFactory, executor);
			}

			return new TraceableExecutorService(this.beanFactory, executor);
		});
	}

	Object createAsyncTaskExecutorProxy(Object bean, boolean cglibProxy,
			AsyncTaskExecutor executor) {
		return getProxiedObject(bean, cglibProxy, executor, () -> {
			if (bean instanceof ThreadPoolTaskScheduler) {
				return new LazyTraceThreadPoolTaskScheduler(this.beanFactory,
						(ThreadPoolTaskScheduler) executor);
			}
			return new LazyTraceAsyncTaskExecutor(this.beanFactory, executor);
		});
	}

	private Object getProxiedObject(Object bean, boolean cglibProxy, Executor executor,
			Supplier<Executor> supplier) {
		ProxyFactoryBean factory = proxyFactoryBean(bean, cglibProxy, executor, supplier);
		try {
			return getObject(factory);
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug(
						"Exception occurred while trying to get a proxy. Will fallback to a different implementation",
						ex);
			}
			try {
				if (bean instanceof ThreadPoolTaskScheduler) {
					if (log.isDebugEnabled()) {
						log.debug(
								"Will wrap ThreadPoolTaskScheduler in its tracing representation due to previous errors");
					}
					return createThreadPoolTaskSchedulerProxy(
							(ThreadPoolTaskScheduler) bean).get();
				}
				else if (bean instanceof ScheduledThreadPoolExecutor) {
					if (log.isDebugEnabled()) {
						log.debug(
								"Will wrap ScheduledThreadPoolExecutor in its tracing representation due to previous errors");
					}
					return createScheduledThreadPoolExecutorProxy(
							(ScheduledThreadPoolExecutor) bean).get();
				}
			}
			catch (Exception ex2) {
				if (log.isDebugEnabled()) {
					log.debug(
							"Fallback for special wrappers failed, will try the tracing representation instead",
							ex2);
				}
			}
			return supplier.get();
		}
	}

	private ProxyFactoryBean proxyFactoryBean(Object bean, boolean cglibProxy,
			Executor executor, Supplier<Executor> supplier) {
		ProxyFactoryBean factory = new ProxyFactoryBean();
		factory.setProxyTargetClass(cglibProxy);
		factory.addAdvice(
				new ExecutorMethodInterceptor<Executor>(executor, this.beanFactory) {
					@Override
					Executor executor(BeanFactory beanFactory, Executor executor) {
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

	private SleuthAsyncProperties asyncConfigurationProperties() {
		if (this.sleuthAsyncProperties == null) {
			this.sleuthAsyncProperties = this.beanFactory
					.getBean(SleuthAsyncProperties.class);
		}
		return this.sleuthAsyncProperties;
	}

	private static <T> boolean anyFinalMethods(T object, Class<T> iface) {
		try {
			for (Method method : ReflectionUtils.getDeclaredMethods(iface)) {
				Method m = ReflectionUtils.findMethod(object.getClass(), method.getName(),
						method.getParameterTypes());
				if (m != null && Modifier.isFinal(m.getModifiers())) {
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

	ExecutorMethodInterceptor(T delegate, BeanFactory beanFactory) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		T executor = executor(this.beanFactory, this.delegate);
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
		return ReflectionUtils.findMethod(object.getClass(), method.getName(),
				method.getParameterTypes());
	}

	T executor(BeanFactory beanFactory, T executor) {
		return (T) new LazyTraceExecutor(beanFactory, executor);
	}

}
