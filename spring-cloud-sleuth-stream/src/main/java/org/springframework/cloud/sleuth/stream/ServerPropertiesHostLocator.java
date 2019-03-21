/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.stream;

import java.lang.invoke.MethodHandles;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.bind.RelaxedPropertyResolver;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;
import org.springframework.cloud.sleuth.Span;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link HostLocator} that retrieves:
 *
 * <ul>
 *     <li><b>service name</b> - either from {@link Span#getProcessId()} or current application name</li>
 *     <li><b>address</b> - from {@link ServerProperties}</li>
 *     <li><b>port</b> - from lazily assigned port or {@link ServerProperties}</li>
 * </ul>
 *
 * You can override the value of service id by {@link ZipkinProperties#getService()}
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class ServerPropertiesHostLocator implements HostLocator, EnvironmentAware {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());
	private static final String IP_ADDRESS_PROP_NAME = "spring.cloud.client.ipAddress";

	private final ServerProperties serverProperties; // Nullable
	private final String appName;
	private final InetUtils inetUtils;
	private final ZipkinProperties zipkinProperties;
	private Integer port; // Lazy assigned
	private RelaxedPropertyResolver resolver;

	@Deprecated
	public ServerPropertiesHostLocator(ServerProperties serverProperties, String appName,
			ZipkinProperties zipkinProperties, InetUtils inetUtils) {
		this.serverProperties = serverProperties;
		this.appName = appName;
		Assert.notNull(this.appName, "appName");
		this.zipkinProperties = zipkinProperties;
		if (inetUtils == null) {
			this.inetUtils = new InetUtils(new InetUtilsProperties());
		} else {
			this.inetUtils = inetUtils;
		}
	}

	public ServerPropertiesHostLocator(ServerProperties serverProperties,
			Environment environment, ZipkinProperties zipkinProperties, InetUtils inetUtils) {
		this(serverProperties, "", zipkinProperties, inetUtils);
		this.resolver = new RelaxedPropertyResolver(environment);
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
		if (this.serverProperties != null && this.serverProperties.getPort() != null && this.serverProperties.getPort() > 0) {
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
		else if (this.resolver != null) {
			address = this.resolver.getProperty(IP_ADDRESS_PROP_NAME, String.class);
		}
		else {
			address = this.inetUtils.findFirstNonLoopbackAddress().getHostAddress();
		}
		return address;
	}

	private String getServiceName(Span span) {
		String serviceName = "unknown";
		if (StringUtils.hasText(this.zipkinProperties.getService().getName())) {
			serviceName = this.zipkinProperties.getService().getName();
		} else if (span.getProcessId() != null) {
			serviceName = span.getProcessId();
		}
		else if (this.resolver != null) {
			serviceName = this.resolver.getProperty("spring.application.name", "unknown");
		}
		if (log.isDebugEnabled()) {
			log.debug("Span will contain serviceName [" + serviceName + "]");
		}
		return serviceName;
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.resolver = new RelaxedPropertyResolver(environment);
	}
}
