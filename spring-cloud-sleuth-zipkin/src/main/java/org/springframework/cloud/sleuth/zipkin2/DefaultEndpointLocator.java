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

package org.springframework.cloud.sleuth.zipkin2;

import java.lang.invoke.MethodHandles;
import java.net.InetAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import zipkin2.Endpoint;

/**
 * {@link EndpointLocator} implementation that:
 *
 * <ul>
 *     <li><b>serviceName</b> - from {@link ServerProperties} or {@link Registration}</li>
 *     <li><b>ip</b> - from {@link ServerProperties}</li>
 *     <li><b>port</b> - from lazily assigned port or {@link ServerProperties}</li>
 * </ul>
 *
 * You can override the name using {@link ZipkinProperties.Service#setName(String)}
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class DefaultEndpointLocator implements EndpointLocator,
		ApplicationListener<ServletWebServerInitializedEvent> {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());
	private static final String IP_ADDRESS_PROP_NAME = "spring.cloud.client.ipAddress";

	private final Registration registration;
	private final ServerProperties serverProperties;
	private final Environment environment;
	private final ZipkinProperties zipkinProperties;
	private Integer port;
	private InetAddress firstNonLoopbackAddress;

	public DefaultEndpointLocator(Registration registration, ServerProperties serverProperties,
			Environment environment, ZipkinProperties zipkinProperties, InetUtils inetUtils) {
		this.registration = registration;
		this.serverProperties = serverProperties;
		this.environment = environment;
		this.zipkinProperties = zipkinProperties;
		this.firstNonLoopbackAddress = findFirstNonLoopbackAddress(inetUtils);
	}

	private InetAddress findFirstNonLoopbackAddress(InetUtils inetUtils) {
		if (inetUtils == null) {
			inetUtils = new InetUtils(new InetUtilsProperties());
		}
		return inetUtils.findFirstNonLoopbackAddress();
	}

	@Override
	public Endpoint local() {
		String serviceName = getLocalServiceName();
		if (log.isDebugEnabled()) {
			log.debug("Span will contain serviceName [" + serviceName + "]");
		}
		Endpoint.Builder builder = Endpoint.newBuilder()
				.serviceName(serviceName)
				.port(getPort());
		return addAddress(builder).build();
	}

	private String getLocalServiceName() {
		if (StringUtils.hasText(this.zipkinProperties.getService().getName())) {
			return this.zipkinProperties.getService().getName();
		} else if (this.registration != null) {
			try {
				return this.registration.getServiceId();
			} catch (RuntimeException e) {
				log.warn("error getting service name from registration", e);
			}
		}
		return this.environment.getProperty("spring.application.name", "unknown");
	}

	@Override
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

	private Endpoint.Builder addAddress(Endpoint.Builder builder) {
		if (this.serverProperties != null && this.serverProperties.getAddress() != null
				&& builder.parseIp(this.serverProperties.getAddress())) {
			return builder;
		}
		else if (this.environment.containsProperty(IP_ADDRESS_PROP_NAME)
				&& builder.parseIp(this.environment.getProperty(IP_ADDRESS_PROP_NAME, String.class))) {
			return builder;
		}
		else {
			return builder.ip(this.firstNonLoopbackAddress);
		}
	}
}
