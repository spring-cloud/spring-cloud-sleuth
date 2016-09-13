/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.InetUtils;

import zipkin.Endpoint;

/**
 * An {@link EndpointLocator} that tries to find local service information from a
 * {@link DiscoveryClient}.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class DiscoveryClientEndpointLocator implements EndpointLocator {

	private DiscoveryClient client;
	private EndpointCache endpointCache;

	public DiscoveryClientEndpointLocator(DiscoveryClient client,
			EndpointCache endpointCache) {
		this.client = client;
		this.endpointCache = endpointCache;
	}

	@Override
	public Endpoint local() {
		ServiceInstance instance = this.client.getLocalServiceInstance();
		if (instance == null) {
			throw new NoServiceInstanceAvailableException();
		}
		return this.endpointCache.getEndpoint(createEndpointFactory(instance),
				getHost(instance), instance.getPort(), instance.getServiceId());
	}

	private EndpointCache.EndpointFactory createEndpointFactory(
			final ServiceInstance instance) {
		return new EndpointCacheImpl.EndpointFactory() {
			@Override
			public Endpoint create() {
				return Endpoint.create(instance.getServiceId(), getIpAddress(instance),
						instance.getPort());
			}
		};
	}

	private static int getIpAddress(ServiceInstance instance) {
		try {
			return InetUtils.getIpAddressAsInt(instance.getHost());
		}
		catch (Exception e) {
			return 0;
		}
	}

	private static String getHost(ServiceInstance instance) {
		try {
			return instance.getHost();
		}
		catch (Exception e) {
			return null;
		}
	}

	static class NoServiceInstanceAvailableException extends RuntimeException {
	}

}
