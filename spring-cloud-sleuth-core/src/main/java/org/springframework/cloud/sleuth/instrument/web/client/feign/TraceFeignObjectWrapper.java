/*
 * Copyright 2013-2020 the original author or authors.
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
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient;
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

	private static final boolean ribbonPresent;

	private static final Log log = LogFactory.getLog(TraceFeignObjectWrapper.class);

	private static final boolean loadBalancerPresent;

	private static final String DELEGATE = "delegate";

	static {
		ribbonPresent = ClassUtils.isPresent(
				"org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient",
				null)
				&& ClassUtils.isPresent(
						"org.springframework.cloud.netflix.ribbon.SpringClientFactory",
						null);
		loadBalancerPresent = ClassUtils.isPresent(
				"org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient",
				null)
				&& ClassUtils.isPresent(
						"org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient",
						null);
	}

	private final BeanFactory beanFactory;

	private CachingSpringLoadBalancerFactory cachingSpringLoadBalancerFactory;

	private Object springClientFactory;

	private Object loadBalancerClient;

	TraceFeignObjectWrapper(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	Object wrap(Object bean) {
		if (bean instanceof Client && !(bean instanceof TracingFeignClient)) {
			if (ribbonPresent && bean instanceof LoadBalancerFeignClient
					&& !(bean instanceof TraceLoadBalancerFeignClient)) {
				return instrumentedFeignRibbonClient(bean);
			}
			if (ribbonPresent && bean instanceof TraceLoadBalancerFeignClient) {
				return bean;
			}
			if (loadBalancerPresent && bean instanceof FeignBlockingLoadBalancerClient
					&& !(bean instanceof TraceFeignBlockingLoadBalancerClient)) {
				return instrumentedFeignLoadBalancerClient(bean);
			}
			if (ribbonPresent && bean instanceof TraceFeignBlockingLoadBalancerClient) {
				return bean;
			}
			return new LazyTracingFeignClient(this.beanFactory, (Client) bean);
		}
		return bean;
	}

	private Object instrumentedFeignLoadBalancerClient(Object bean) {
		if (AopUtils.getTargetClass(bean).equals(FeignBlockingLoadBalancerClient.class)) {
			FeignBlockingLoadBalancerClient client = ProxyUtils.getTargetObject(bean);
			return new TraceFeignBlockingLoadBalancerClient(
					(Client) new TraceFeignObjectWrapper(this.beanFactory)
							.wrap(client.getDelegate()),
					(BlockingLoadBalancerClient) loadBalancerClient(), this.beanFactory);
		}
		else {
			FeignBlockingLoadBalancerClient client = ((FeignBlockingLoadBalancerClient) bean);
			try {
				Field delegate = FeignBlockingLoadBalancerClient.class
						.getDeclaredField(DELEGATE);
				delegate.setAccessible(true);
				delegate.set(client, new TraceFeignObjectWrapper(this.beanFactory)
						.wrap(client.getDelegate()));
			}
			catch (NoSuchFieldException | IllegalArgumentException
					| IllegalAccessException | SecurityException e) {
				log.warn(EXCEPTION_WARNING, e);
			}
			return new TraceFeignBlockingLoadBalancerClient(client,
					(BlockingLoadBalancerClient) loadBalancerClient(), this.beanFactory);
		}
	}

	private Object instrumentedFeignRibbonClient(Object bean) {
		if (AopUtils.getTargetClass(bean).equals(LoadBalancerFeignClient.class)) {
			LoadBalancerFeignClient client = ((LoadBalancerFeignClient) bean);
			return new TraceLoadBalancerFeignClient(
					(Client) new TraceFeignObjectWrapper(this.beanFactory)
							.wrap(client.getDelegate()),
					factory(), (SpringClientFactory) clientFactory(), this.beanFactory);
		}
		else {
			LoadBalancerFeignClient client = ((LoadBalancerFeignClient) bean);
			try {
				Field delegate = LoadBalancerFeignClient.class.getDeclaredField(DELEGATE);
				delegate.setAccessible(true);
				delegate.set(client, new TraceFeignObjectWrapper(this.beanFactory)
						.wrap(client.getDelegate()));
			}
			catch (NoSuchFieldException | IllegalArgumentException
					| IllegalAccessException | SecurityException e) {
				log.warn(EXCEPTION_WARNING, e);
			}
			return new TraceLoadBalancerFeignClient(client, factory(),
					(SpringClientFactory) clientFactory(), this.beanFactory);
		}
	}

	private CachingSpringLoadBalancerFactory factory() {
		if (this.cachingSpringLoadBalancerFactory == null) {
			this.cachingSpringLoadBalancerFactory = this.beanFactory
					.getBean(CachingSpringLoadBalancerFactory.class);
		}
		return this.cachingSpringLoadBalancerFactory;
	}

	private Object clientFactory() {
		if (this.springClientFactory == null) {
			this.springClientFactory = this.beanFactory
					.getBean(SpringClientFactory.class);
		}
		return this.springClientFactory;
	}

	private Object loadBalancerClient() {
		if (loadBalancerClient == null) {
			loadBalancerClient = beanFactory.getBean(BlockingLoadBalancerClient.class);
		}
		return loadBalancerClient;
	}

}
