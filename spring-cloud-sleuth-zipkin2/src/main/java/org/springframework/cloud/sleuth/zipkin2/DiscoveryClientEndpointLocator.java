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

package org.springframework.cloud.sleuth.zipkin2;

import java.lang.invoke.MethodHandles;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.util.StringUtils;

import zipkin2.Endpoint;

/**
 * An {@link EndpointLocator} that tries to find local service information from a
 * {@link DiscoveryClient}.
 *
 * You can override the name using {@link ZipkinProperties.Service#setName(String)}
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class DiscoveryClientEndpointLocator implements EndpointLocator {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final DiscoveryClient client;
	private final ZipkinProperties zipkinProperties;

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
		String serviceName = StringUtils.hasText(this.zipkinProperties.getService().getName()) ?
				this.zipkinProperties.getService().getName() : instance.getServiceId();
		if (log.isDebugEnabled()) {
			log.debug("Span will contain serviceName [" + serviceName + "]");
		}
		Endpoint.Builder builder = Endpoint.newBuilder()
				.serviceName(serviceName)
				.port(instance.getPort());
		try {
			builder.ip(instance.getHost());
		} catch (RuntimeException e) {
			if (log.isDebugEnabled()) {
				log.debug("Couldn't parse the service host", e);
			}
		}
		return builder.build();
	}

	static class NoServiceInstanceAvailableException extends RuntimeException { }
}
