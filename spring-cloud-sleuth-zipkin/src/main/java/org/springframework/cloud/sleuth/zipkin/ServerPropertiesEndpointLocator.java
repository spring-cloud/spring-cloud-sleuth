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

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.cloud.sleuth.util.LocalAdressResolver;
import org.springframework.context.event.EventListener;

import zipkin.Endpoint;

/**
 * {@link EndpointLocator} implementation that:
 *
 * <ul>
 * <li><b>address</b> - from {@link ServerProperties}</li>
 * <li><b>port</b> - from lazily assigned port or {@link ServerProperties}</li>
 * </ul>
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class ServerPropertiesEndpointLocator implements EndpointLocator {

	private final ServerProperties serverProperties;
	private final String appName;
	private Integer port;
	private final LocalAdressResolver localAdressResolver;

	public ServerPropertiesEndpointLocator(ServerProperties serverProperties,
			String appName,LocalAdressResolver localAdressResolver) {
		this.serverProperties = serverProperties;
		this.appName = appName;
		this.localAdressResolver = localAdressResolver;
	}

	@Override
	public Endpoint local() {
		int address = getAddress();
		Integer port = getPort();
		return Endpoint.create(this.appName, address, port);
	}

	@EventListener(EmbeddedServletContainerInitializedEvent.class)
	public void grabPort(EmbeddedServletContainerInitializedEvent event) {
		this.port = event.getEmbeddedServletContainer().getPort();
	}

	private Integer getPort() {
		if (this.port != null) {
			return this.port;
		}
		Integer port;
		if (this.serverProperties != null && this.serverProperties.getPort() != null) {
			port = this.serverProperties.getPort();
		}
		else {
			port = 8080;
		}
		return port;
	}

	private int getAddress() {
		if (this.serverProperties != null && this.serverProperties.getAddress() != null) {
			return this.localAdressResolver.asInt(this.serverProperties.getAddress());
		}
		else {
			return this.localAdressResolver.getLocalIp4AddressAsInt();
		}
	}
}
