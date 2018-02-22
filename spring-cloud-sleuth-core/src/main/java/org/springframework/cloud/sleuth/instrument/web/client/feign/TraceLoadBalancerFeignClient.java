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

import java.io.IOException;

import feign.Client;
import feign.Request;
import feign.Response;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.openfeign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;

/**
 * We need to wrap the {@link LoadBalancerFeignClient} into a trace representation
 * due to casts in {@link org.springframework.cloud.openfeign.FeignClientFactoryBean}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.7
 */
class TraceLoadBalancerFeignClient extends LoadBalancerFeignClient {

	private final BeanFactory beanFactory;

	TraceLoadBalancerFeignClient(Client delegate,
			CachingSpringLoadBalancerFactory lbClientFactory,
			SpringClientFactory clientFactory, BeanFactory beanFactory) {
		super(delegate, lbClientFactory, clientFactory);
		this.beanFactory = beanFactory;
	}

	@Override public Response execute(Request request, Request.Options options)
			throws IOException {
		return ((Client) new TraceFeignObjectWrapper(this.beanFactory).wrap(
				(Client) TraceLoadBalancerFeignClient.super::execute)).execute(request, options);
	}

}
