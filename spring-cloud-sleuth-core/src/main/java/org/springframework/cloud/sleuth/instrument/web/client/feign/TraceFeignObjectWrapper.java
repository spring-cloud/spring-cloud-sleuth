/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import feign.Client;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.openfeign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.util.ClassUtils;

/**
 * Class that wraps Feign related classes into their Trace representative
 *
 * @author Marcin Grzejszczak
 * @since 1.0.1
 */
final class TraceFeignObjectWrapper {

	private final BeanFactory beanFactory;

	private CachingSpringLoadBalancerFactory cachingSpringLoadBalancerFactory;
	private Object springClientFactory;
	private static final boolean ribbonPresent;

	static {
		ribbonPresent =
				ClassUtils.isPresent("org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient", null) &&
				ClassUtils.isPresent("org.springframework.cloud.netflix.ribbon.SpringClientFactory", null);
	}

	TraceFeignObjectWrapper(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	Object wrap(Object bean) {
		if (bean instanceof Client && !(bean instanceof TracingFeignClient)) {
			if (ribbonPresent &&
					bean instanceof LoadBalancerFeignClient && !(bean instanceof TraceLoadBalancerFeignClient)) {
				LoadBalancerFeignClient client = ((LoadBalancerFeignClient) bean);
				return new TraceLoadBalancerFeignClient(
						(Client) new TraceFeignObjectWrapper(this.beanFactory)
								.wrap(client.getDelegate()),
						factory(), (SpringClientFactory) clientFactory(), this.beanFactory);
			} else if (ribbonPresent &&
					bean instanceof TraceLoadBalancerFeignClient) {
				return bean;
			}
			return new LazyTracingFeignClient(this.beanFactory, (Client) bean);
		}
		return bean;
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

}
