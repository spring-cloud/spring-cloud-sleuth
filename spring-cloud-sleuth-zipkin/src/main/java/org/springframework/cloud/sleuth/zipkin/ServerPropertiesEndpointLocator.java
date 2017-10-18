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

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import zipkin.Endpoint;

/**
 * {@link EndpointLocator} implementation that:
 *
 * <ul>
 *     <li><b>address</b> - from {@link ServerProperties}</li>
 *     <li><b>port</b> - from lazily assigned port or {@link ServerProperties}</li>
 * </ul>
 *
 * You can override the name using {@link ZipkinProperties.Service#setName(String)}
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class ServerPropertiesEndpointLocator implements EndpointLocator {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());
	private static final String IP_ADDRESS_PROP_NAME = "spring.cloud.client.ipAddress";

	private final ServerProperties serverProperties;
	private final ZipkinProperties zipkinProperties;
	private final Environment environment;
	private Integer port;

	public ServerPropertiesEndpointLocator(ServerProperties serverProperties,
			Environment environment, ZipkinProperties zipkinProperties) {
		this.serverProperties = serverProperties;
		this.environment = environment;
		this.zipkinProperties = zipkinProperties;
	}

	@Override
	public Endpoint local() {
		String serviceName = serviceName();
		if (log.isDebugEnabled()) {
			log.debug("Span will contain serviceName [" + serviceName + "]");
		}
		return Endpoint.builder()
				.serviceName(serviceName)
				.ipv4(getAddress())
				.port(getPort())
				.build();
	}

	private String serviceName() {
		if (StringUtils.hasText(this.zipkinProperties.getService().getName())) {
			return this.zipkinProperties.getService().getName();
		}
		return this.environment.getProperty("spring.application.name", "unknown");
	}

	@EventListener(ServletWebServerInitializedEvent.class)
	public void onApplicationEvent(ServletWebServerInitializedEvent event) {
		this.port = event.getSource().getPort();
	}

	private Integer getPort() {
		if (this.port!=null) {
			return this.port;
		}
		Integer port;
		if (this.serverProperties!=null && this.serverProperties.getPort() != null && this.serverProperties.getPort() > 0) {
			port = this.serverProperties.getPort();
		}
		else {
			port = 8080;
		}
		return port;
	}

	private int getAddress() {
		if (this.serverProperties != null && this.serverProperties.getAddress() != null) {
			return ByteBuffer.wrap(this.serverProperties.getAddress().getAddress())
					.getInt();
		}
		String ipAddress = this.environment
				.getProperty(IP_ADDRESS_PROP_NAME, String.class);
		return InetUtils.getIpAddressAsInt(ipAddress);
	}
}
