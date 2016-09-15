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

package org.springframework.cloud.sleuth.stream;

import java.net.InetAddress;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.sleuth.Span;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * An {@link HostLocator} that tries to find local service information from a
 * {@link DiscoveryClient}.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class DiscoveryClientHostLocator implements HostLocator {

	private DiscoveryClient client;
	private SleuthStreamProperties sleuthStreamProperties;
	private Host cachedHost;
	private String cachedForHostname;

	public DiscoveryClientHostLocator(DiscoveryClient client,
			SleuthStreamProperties sleuthStreamProperties) {
		this.client = client;
		this.sleuthStreamProperties = sleuthStreamProperties;
		Assert.notNull(this.client, "client");
	}

	@Override
	public synchronized Host locate(Span span) {
		ServiceInstance instance = this.client.getLocalServiceInstance();
		String host = getHost(instance);

		if (!this.sleuthStreamProperties.isLocalEndpointCachingEnabled()) {
			return new Host(instance.getServiceId(), getIpAddress(host),
					instance.getPort());
		}
		if (this.cachedHost == null || !ObjectUtils.nullSafeEquals(host, this.cachedForHostname)) {
			this.cachedHost = new Host(instance.getServiceId(), getIpAddress(host),
					instance.getPort());
			this.cachedForHostname = host;
		}
		return this.cachedHost;
	}

	private String getIpAddress(String host) {
		if (StringUtils.isEmpty(host)) {
			return "0.0.0.0";
		}
		try {
			InetAddress address = InetAddress.getByName(host);
			return address.getHostAddress();
		}
		catch (Exception e) {
			return "0.0.0.0";
		}
	}

	private String getHost(ServiceInstance instance) {
		try {
			return instance.getHost();
		}
		catch (Exception e) {
			return null;
		}

	}

}
