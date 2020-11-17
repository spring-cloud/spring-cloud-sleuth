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

package org.springframework.cloud.sleuth.zipkin2;

import java.net.URI;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;

class LoadBalancerClientZipkinLoadBalancer implements ZipkinLoadBalancer {

	private final LoadBalancerClient loadBalancerClient;

	private final ZipkinProperties zipkinProperties;

	LoadBalancerClientZipkinLoadBalancer(LoadBalancerClient loadBalancerClient, ZipkinProperties zipkinProperties) {
		this.loadBalancerClient = loadBalancerClient;
		this.zipkinProperties = zipkinProperties;
	}

	@Override
	public URI instance() {
		if (this.loadBalancerClient != null) {
			URI uri = URI.create(this.zipkinProperties.getBaseUrl());
			String host = uri.getHost();
			ServiceInstance instance = this.loadBalancerClient.choose(host);
			if (instance != null) {
				return instance.getUri();
			}
		}
		return URI.create(this.zipkinProperties.getBaseUrl());
	}

}
