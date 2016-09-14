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

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.util.ObjectUtils;

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
	private ZipkinProperties zipkinProperties;
	private final AtomicReference<Host> resolvedHostCache = new AtomicReference<>();

	public DiscoveryClientEndpointLocator(DiscoveryClient client,
			ZipkinProperties zipkinProperties) {
		this.client = client;
		this.zipkinProperties = zipkinProperties;
	}

	@Override
	public Endpoint local() {
		ServiceInstance instance = this.client.getLocalServiceInstance();
		if (instance == null) {
			throw new NoServiceInstanceAvailableException();
		}
		return Endpoint.create(instance.getServiceId(), getIpAddress(instance),
				instance.getPort());

	}

	private int getIpAddress(ServiceInstance serviceInstance) {
		String hostName;
		try {
			hostName = serviceInstance.getHost();
		}
		catch (Exception e) {
			// this is because there was a test that assumed getHost
			// can throw an exception...
			return 0;
		}
		return this.zipkinProperties.isLocalEndpointCachingEnabled()
				? updateCacheAndGetIpAddressAsInt(hostName)
				: resolveIpAddressToInt(hostName);
	}

	private static int resolveIpAddressToInt(String hostName) {
		try {
			return InetUtils.getIpAddressAsInt(hostName);
		}
		catch (Exception e) {
			return 0;
		}
	}

	/**
	 * Based on java 1.8 AtomicReference#updateAndGet...
	 * @param hostName
	 * @return
	 */
	private int updateCacheAndGetIpAddressAsInt(String hostName) {
		Host prev, next;
		do {
			prev = this.resolvedHostCache.get();
			if (prev == null || !ObjectUtils.nullSafeEquals(prev.hostName, hostName)) {
				next = new Host(hostName, resolveIpAddressToInt(hostName));
			}
			else {
				next = prev;
			}
		}
		while (!this.resolvedHostCache.compareAndSet(prev, next));
		return next.host;
	}

	static class NoServiceInstanceAvailableException extends RuntimeException {
	}

	private static class Host {
		private final String hostName;
		private final int host;

		private Host(String hostName, int host) {
			this.hostName = hostName;
			this.host = host;
		}
	}
}
