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

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.sleuth.Span;
import org.springframework.context.event.EventListener;
import org.springframework.util.Assert;

import java.net.InetAddress;

/**
 * A {@link HostLocator} that retrieves:
 *
 * <ul>
 * <li><b>service name</b> - either from {@link Span#getProcessId()} or current
 * application name</li>
 * <li><b>address</b> - from {@link ServerProperties}</li>
 * <li><b>port</b> - from lazily assigned port or {@link ServerProperties}</li>
 * </ul>
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class ServerPropertiesHostLocator implements HostLocator {

	private final ServerProperties serverProperties; // Nullable
	private final String appName;
	private final InetUtils inetUtils;
	private Integer port; // Lazy assigned

	public ServerPropertiesHostLocator(ServerProperties serverProperties, String appName,
			InetUtils inetUtils) {
		this.serverProperties = serverProperties;
		this.appName = appName;
		this.inetUtils = inetUtils;
		Assert.notNull(this.appName, "appName");
	}

	@Override
	public Host locate(Span span) {
		String serviceName = getServiceName(span);
		String address = getAddress();
		Integer port = getPort();
		return new Host(serviceName, address, port);
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

	private String getAddress() {
		String address;
		if (this.serverProperties != null && this.serverProperties.getAddress() != null) {
			address = this.serverProperties.getAddress().getHostAddress();
		}
		else {
			address = getFirstNonLoopbackAddress().getHostAddress();
		}
		return address;
	}

	private InetAddress getFirstNonLoopbackAddress() {
		return this.inetUtils.findFirstNonLoopbackAddress();
	}

	private String getServiceName(Span span) {
		String serviceName;
		if (span.getProcessId() != null) {
			serviceName = span.getProcessId();
		}
		else {
			serviceName = this.appName;
		}
		return serviceName;
	}

}
