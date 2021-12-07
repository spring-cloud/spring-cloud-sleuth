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

package org.springframework.cloud.sleuth.autoconfig.instrument.tx;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.transaction.TransactionManager;
import org.springframework.util.ReflectionUtils;

abstract class AbstractTransactionManagerInstrumenter<T extends TransactionManager> {

	private static final Log log = LogFactory.getLog(AbstractTransactionManagerInstrumenter.class);

	protected final BeanFactory beanFactory;

	private final Class<T> classToInstrument;

	AbstractTransactionManagerInstrumenter(BeanFactory beanFactory, Class<T> classToInstrument) {
		this.beanFactory = beanFactory;
		this.classToInstrument = classToInstrument;
	}

	private static <T> boolean anyFinalMethods(T object, Class classToCheckAgainst) {
		try {
			for (Method method : ReflectionUtils.getAllDeclaredMethods(classToCheckAgainst)) {
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

	boolean isApplicableForInstrumentation(Object bean) {
		return isNotYetTraced(bean) && !(tracedClass().isAssignableFrom(bean.getClass()));
	}

	private boolean isNotYetTraced(Object bean) {
		return this.classToInstrument.isAssignableFrom(bean.getClass());
	}

	@SuppressWarnings("rawtypes")
	abstract Class tracedClass();

	abstract T wrap(T transactionManager);

	Object instrument(Object bean) {
		if (!isApplicableForInstrumentation(bean)) {
			return bean;
		}
		return wrapManager(bean);
	}

	private Object wrapManager(Object bean) {
		T manager = (T) bean;
		boolean methodFinal = anyFinalMethods(manager, this.classToInstrument);
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean cglibProxy = !methodFinal && !classFinal;
		try {
			return createProxy(bean, cglibProxy, new TransactionManagerMethodInterceptor<>(this, manager));
		}
		catch (AopConfigException ex) {
			if (cglibProxy) {
				if (log.isDebugEnabled()) {
					log.debug("Exception occurred while trying to create a proxy, falling back to JDK proxy", ex);
				}
				return createProxy(bean, false, new TransactionManagerMethodInterceptor<>(this, manager));
			}
			throw ex;
		}
	}

	private Object getObject(ProxyFactoryBean factory) {
		return factory.getObject();
	}

	@SuppressWarnings("unchecked")
	private Object createProxy(Object bean, boolean cglibProxy, Advice advice) {
		ProxyFactoryBean factory = new ProxyFactoryBean();
		factory.setProxyTargetClass(cglibProxy);
		factory.addAdvice(advice);
		factory.setTarget(bean);
		return getObject(factory);
	}

	static class TransactionManagerMethodInterceptor<T extends TransactionManager> implements MethodInterceptor {

		private static final Map<TransactionManager, TransactionManager> CACHE = new ConcurrentHashMap<>();

		private final AbstractTransactionManagerInstrumenter<T> parent;

		private final T delegate;

		TransactionManagerMethodInterceptor(AbstractTransactionManagerInstrumenter<T> parent, T delegate) {
			this.parent = parent;
			this.delegate = delegate;
		}

		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {
			T tracedDelegate = traceDelegate();
			Method methodOnTracedBean = getMethod(invocation, tracedDelegate);
			if (methodOnTracedBean != null) {
				try {
					return methodOnTracedBean.invoke(tracedDelegate, invocation.getArguments());
				}
				catch (InvocationTargetException ex) {
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

		@SuppressWarnings("unchecked")
		private T traceDelegate() {
			return (T) CACHE.computeIfAbsent(this.delegate, o -> parent.wrap((T) o));
		}

	}

}
