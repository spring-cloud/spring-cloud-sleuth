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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.lang.reflect.Field;

import feign.Client;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancedRetryFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.loadbalancer.RetryableFeignBlockingLoadBalancerClient;
import org.springframework.cloud.util.ProxyUtils;
import org.springframework.util.ClassUtils;

/**
 * Class that wraps Feign related classes into their Trace representative.
 *
 * @author Marcin Grzejszczak
 * @author Olga Maciaszek-Sharma
 * @since 1.0.1
 */
final class TraceFeignObjectWrapper {

	public static final String EXCEPTION_WARNING = "Exception occurred while trying to access the delegate's field. Will fallback to default instrumentation mechanism, which means that the delegate might not be instrumented";

	private static final Log log = LogFactory.getLog(TraceFeignObjectWrapper.class);

	private static final boolean loadBalancerPresent;

	private static final String DELEGATE = "delegate";

	static {
		loadBalancerPresent = ClassUtils
				.isPresent("org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient", null)
				&& ClassUtils.isPresent(
						"org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient", null)
				&& ClassUtils.isPresent("org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory",
						null);
	}

	private final BeanFactory beanFactory;

	private Object loadBalancerClient;

	private LoadBalancerProperties loadBalancerProperties;

	private Object loadBalancerRetryFactory;

	private Object loadBalancerClientFactory;

	TraceFeignObjectWrapper(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	Object wrap(Object bean) {
		if (bean instanceof Client && !(bean instanceof TracingFeignClient)
				&& !(bean instanceof LazyTracingFeignClient)) {
			if (loadBalancerPresent && bean instanceof FeignBlockingLoadBalancerClient
					&& !(bean instanceof TraceFeignBlockingLoadBalancerClient)) {
				return instrumentedFeignLoadBalancerClient(bean);
			}
			if (loadBalancerPresent && bean instanceof RetryableFeignBlockingLoadBalancerClient
					&& !(bean instanceof TraceRetryableFeignBlockingLoadBalancerClient)) {
				return instrumentedRetryableFeignLoadBalancerClient(bean);
			}
			return new LazyTracingFeignClient(this.beanFactory, (Client) bean);
		}
		return bean;
	}

	private Object instrumentedFeignLoadBalancerClient(Object bean) {
		if (AopUtils.getTargetClass(bean).equals(FeignBlockingLoadBalancerClient.class)) {
			FeignBlockingLoadBalancerClient client = ProxyUtils.getTargetObject(bean);
			return new TraceFeignBlockingLoadBalancerClient(
					(Client) new TraceFeignObjectWrapper(this.beanFactory).wrap(client.getDelegate()),
					(LoadBalancerClient) loadBalancerClient(), loadBalancerProperties(),
					(LoadBalancerClientFactory) loadBalancerClientFactory(), this.beanFactory);
		}
		else {
			FeignBlockingLoadBalancerClient client = ProxyUtils.getTargetObject(bean);
			try {
				Field delegate = FeignBlockingLoadBalancerClient.class.getDeclaredField(DELEGATE);
				delegate.setAccessible(true);
				delegate.set(client, new TraceFeignObjectWrapper(this.beanFactory).wrap(client.getDelegate()));
			}
			catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException | SecurityException e) {
				log.warn(EXCEPTION_WARNING, e);
			}
			return new TraceFeignBlockingLoadBalancerClient(client, (LoadBalancerClient) loadBalancerClient(),
					loadBalancerProperties(), (LoadBalancerClientFactory) loadBalancerClientFactory(),
					this.beanFactory);
		}
	}

	private Object instrumentedRetryableFeignLoadBalancerClient(Object bean) {
		if (AopUtils.getTargetClass(bean).equals(RetryableFeignBlockingLoadBalancerClient.class)) {
			RetryableFeignBlockingLoadBalancerClient client = ProxyUtils.getTargetObject(bean);
			return new TraceRetryableFeignBlockingLoadBalancerClient(
					(Client) new TraceFeignObjectWrapper(beanFactory).wrap(client.getDelegate()),
					(BlockingLoadBalancerClient) loadBalancerClient(),
					(LoadBalancedRetryFactory) loadBalancerRetryFactory(), loadBalancerProperties(),
					(LoadBalancerClientFactory) loadBalancerClientFactory(), beanFactory);
		}
		else {
			RetryableFeignBlockingLoadBalancerClient client = ((RetryableFeignBlockingLoadBalancerClient) bean);
			try {
				Field delegate = RetryableFeignBlockingLoadBalancerClient.class.getDeclaredField(DELEGATE);
				delegate.setAccessible(true);
				delegate.set(client, new TraceFeignObjectWrapper(beanFactory).wrap(client.getDelegate()));
			}
			catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException | SecurityException e) {
				log.warn(EXCEPTION_WARNING, e);
			}
			return new TraceRetryableFeignBlockingLoadBalancerClient(client,
					(BlockingLoadBalancerClient) loadBalancerClient(),
					(LoadBalancedRetryFactory) loadBalancerRetryFactory(), loadBalancerProperties(),
					(LoadBalancerClientFactory) loadBalancerClientFactory(), beanFactory);
		}
	}

	private Object loadBalancerClient() {
		if (loadBalancerClient == null) {
			loadBalancerClient = beanFactory.getBean(LoadBalancerClient.class);
		}
		return loadBalancerClient;
	}

	private LoadBalancerProperties loadBalancerProperties() {
		if (loadBalancerProperties == null) {
			loadBalancerProperties = beanFactory.getBean(LoadBalancerProperties.class);
		}
		return loadBalancerProperties;
	}

	private Object loadBalancerRetryFactory() {
		if (loadBalancerRetryFactory == null) {
			loadBalancerRetryFactory = beanFactory.getBean(LoadBalancedRetryFactory.class);
		}
		return loadBalancerRetryFactory;
	}

	private Object loadBalancerClientFactory() {
		if (loadBalancerClientFactory == null) {
			loadBalancerClientFactory = beanFactory.getBean(LoadBalancerClientFactory.class);
		}
		return loadBalancerClientFactory;
	}

}
