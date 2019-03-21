/*
 * Copyright 2013-2018 the original author or authors.
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ReflectionUtils;

/**
 * Bean post processor that wraps a call to an {@link Executor} either in a
 * JDK or CGLIB proxy. Depending on whether the implementation has a final
 * method or is final.
 *
 * @author Marcin Grzejszczak
 * @author Jesus Alonso
 * @author Denys Ivano
 * @since 1.1.4
 */
class ExecutorBeanPostProcessor implements BeanPostProcessor {

	private static final Log log = LogFactory.getLog(
			ExecutorBeanPostProcessor.class);

	private final BeanFactory beanFactory;

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
		if (bean instanceof ThreadPoolTaskExecutor) {
			return wrapThreadPoolTaskExecutor(bean);
		} else if (bean instanceof ExecutorService) {
			return wrapExecutorService(bean);
		} else if (bean instanceof Executor) {
			return wrapExecutor(bean);
		}
		return bean;
	}

	private Object wrapExecutor(Object bean) {
		Method execute = ReflectionUtils.findMethod(bean.getClass(), "execute",
				Runnable.class);
		boolean methodFinal = Modifier.isFinal(execute.getModifiers());
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean cglibProxy = !methodFinal && !classFinal;
		Executor executor = (Executor) bean;
		try {
			return createProxy(bean, cglibProxy,
					new ExecutorMethodInterceptor(executor, this.beanFactory));
		}
		catch (AopConfigException ex) {
			if (cglibProxy) {
				if (log.isDebugEnabled()) {
					log.debug(
							"Exception occurred while trying to create a proxy, falling back to JDK proxy",
							ex);
				}
				return createProxy(bean, false, new ExecutorMethodInterceptor(executor, this.beanFactory));
			}
			throw ex;
		}
	}

	private Object wrapThreadPoolTaskExecutor(Object bean) {
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean cglibProxy = !classFinal;
		ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) bean;
		return createThreadPoolTaskExecutorProxy(bean, cglibProxy, executor);
	}

	private Object wrapExecutorService(Object bean) {
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean cglibProxy = !classFinal;
		ExecutorService executor = (ExecutorService) bean;
		return createExecutorServiceProxy(bean, cglibProxy, executor);
	}

	Object createThreadPoolTaskExecutorProxy(Object bean, boolean cglibProxy,
			ThreadPoolTaskExecutor executor) {
		return getProxiedObject(bean, cglibProxy, executor,
				() -> new LazyTraceThreadPoolTaskExecutor(this.beanFactory, executor));
	}

	Object createExecutorServiceProxy(Object bean, boolean cglibProxy,
			ExecutorService executor) {
		return getProxiedObject(bean, cglibProxy, executor,
				() -> new TraceableExecutorService(this.beanFactory, executor));
	}

	private Object getProxiedObject(Object bean, boolean cglibProxy, Executor executor,
			Supplier<Executor> supplier) {
		ProxyFactoryBean factory = new ProxyFactoryBean();
		factory.setProxyTargetClass(cglibProxy);
		factory.addAdvice(new ExecutorMethodInterceptor<Executor>(executor,
				this.beanFactory) {
			@Override
			<T extends Executor> T executor(BeanFactory beanFactory, T executor) {
				return (T) supplier.get();
			}
		});
		factory.setTarget(bean);
		try {
			return getObject(factory);
		} catch (Exception e) {
			if (log.isDebugEnabled()) {
				log.debug("Exception occurred while trying to get a proxy. Will fallback to a different implementation", e);
			}
			return supplier.get();
		}
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
		return factory.getObject();
	}
}

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
			} catch (InvocationTargetException ex) {
				// gh-1092: throw the target exception (if present)
				Throwable cause = ex.getCause();
				throw (cause != null) ? cause : ex;
			}
		}
		return invocation.proceed();
	}

	private Method getMethod(MethodInvocation invocation, Object object) {
		Method method = invocation.getMethod();
		return ReflectionUtils
				.findMethod(object.getClass(), method.getName(), method.getParameterTypes());
	}

	<T extends Executor> T executor(BeanFactory beanFactory, T executor) {
		return (T) new LazyTraceExecutor(beanFactory, executor);
	}
}
