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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the host from which the span was sent
 *
 * @author Dave Syer
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Host {

	private String serviceName;
	private String address;
	private Integer port;

	@SuppressWarnings("unused")
	private Host() {
	}

	public Host(String serviceName, String address, Integer port) {
		this.serviceName = serviceName;
		this.address = address;
		this.port = port;
	}

	@JsonIgnore
	public int getIpv4() {
		InetAddress inetAddress = null;
		try {
			inetAddress = InetAddress.getByName(this.address);
		}
		catch (final UnknownHostException e) {
			throw new IllegalArgumentException(e);
		}
		return ByteBuffer.wrap(inetAddress.getAddress()).getInt();
	}

	public String getServiceName() {
		return this.serviceName;
	}

	public String getAddress() {
		return this.address;
	}

	public Integer getPort() {
		return this.port;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public void setPort(Integer port) {
		this.port = port;
	}
}
