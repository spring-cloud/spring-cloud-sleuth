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
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @author Dave Syer
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL) public class Host {

	private String serviceName;
	private String address;
	private Integer port;

	public Host(String serviceName, String address, Integer port) {
		this.serviceName = serviceName;
		this.address = address;
		this.port = port;
	}

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

	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof Host))
			return false;
		final Host other = (Host) o;
		if (!other.canEqual((Object) this))
			return false;
		final Object this$serviceName = this.serviceName;
		final Object other$serviceName = other.serviceName;
		if (this$serviceName == null ?
				other$serviceName != null :
				!this$serviceName.equals(other$serviceName))
			return false;
		final Object this$address = this.address;
		final Object other$address = other.address;
		if (this$address == null ?
				other$address != null :
				!this$address.equals(other$address))
			return false;
		final Object this$port = this.port;
		final Object other$port = other.port;
		if (this$port == null ? other$port != null : !this$port.equals(other$port))
			return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $serviceName = this.serviceName;
		result = result * PRIME + ($serviceName == null ? 0 : $serviceName.hashCode());
		final Object $address = this.address;
		result = result * PRIME + ($address == null ? 0 : $address.hashCode());
		final Object $port = this.port;
		result = result * PRIME + ($port == null ? 0 : $port.hashCode());
		return result;
	}

	protected boolean canEqual(Object other) {
		return other instanceof Host;
	}

	public String toString() {
		return "org.springframework.cloud.sleuth.stream.Host(serviceName="
				+ this.serviceName + ", address=" + this.address + ", port=" + this.port
				+ ")";
	}
}
