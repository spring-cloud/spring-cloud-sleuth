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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.cloud.sleuth.Span;
import org.springframework.context.event.EventListener;

import com.twitter.zipkin.gen.Endpoint;

/**
 * @author Dave Syer
 *
 */
public class ServerPropertiesEndpointLocator implements EndpointLocator {

	@Value("${spring.application.name:application}")
	private String appName;

	private ServerProperties serverProperties;

	private Integer port;

	public ServerPropertiesEndpointLocator(ServerProperties serverProperties) {
		this.serverProperties = serverProperties;
	}

	@Override
	public Endpoint locate(Span span) {
		String serviceName = getServiceName(span);
		int address = getAddress();
		Integer port = getPort();
		Endpoint ep = new Endpoint(address, port.shortValue(), serviceName);
		return ep;
	}

	@EventListener(EmbeddedServletContainerInitializedEvent.class)
	public void grabPort(EmbeddedServletContainerInitializedEvent event) {
		this.port = event.getEmbeddedServletContainer().getPort();
	}

	private Integer getPort() {
		if (this.port!=null) {
			return this.port;
		}
		Integer port;
		if (this.serverProperties!=null && this.serverProperties.getPort() != null) {
			port = this.serverProperties.getPort();
		}
		else {
			port = 8080;
		}
		return port;
	}

	private int getAddress() {
		String address;
		if (this.serverProperties!=null && this.serverProperties.getAddress() != null) {
			address = this.serverProperties.getAddress().getHostAddress();
		}
		else {
			address = "127.0.0.1";
		}
		return ipAddressToInt(address);
	}

	private String getServiceName(Span span) {
		String serviceName;
		if (span.getProcessId() != null) {
			serviceName = span.getProcessId().toLowerCase();
		}
		else {
			serviceName = this.appName;
		}
		return serviceName;
	}

	private int ipAddressToInt(final String ip) {
		InetAddress inetAddress = null;
		try {
			inetAddress = InetAddress.getByName(ip);
		}
		catch (final UnknownHostException e) {
			throw new IllegalArgumentException(e);
		}
		return ByteBuffer.wrap(inetAddress.getAddress()).getInt();
	}

}
