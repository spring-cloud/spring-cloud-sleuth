/*
 * Copyright 2013-2017 the original author or authors.
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

import java.net.URI;
import java.util.Map;

import org.junit.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.commons.util.InetUtils;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class DiscoveryClientHostLocatorTest {

	@Test(expected = IllegalArgumentException.class)
	public void should_throw_exception_when_no_registration_is_present() throws Exception {
		new DiscoveryClientHostLocator((Registration)null, new ZipkinProperties());
	}

	private DiscoveryClientHostLocator hostLocator(ServiceInstance serviceInstance) {
		return hostLocator(serviceInstance, new ZipkinProperties());
	}

	private DiscoveryClientHostLocator hostLocator(ServiceInstance serviceInstance, ZipkinProperties zipkinProperties) {
		return new DiscoveryClientHostLocator(serviceInstance, zipkinProperties);
	}

	@Test
	public void should_create_Host_with_0_ip_when_exception_occurs_on_resolving_host() throws Exception {
		DiscoveryClientHostLocator hostLocator = hostLocator(serviceInstanceWithInvalidHost());

		Host host = hostLocator.locate(null);

		then(host.getServiceName()).isEqualTo("serviceId");
		then(host.getPort()).isEqualTo((short)8_000);
		then(host.getIpv4()).isEqualTo(0);
	}

	@Test
	public void should_create_valid_Host_when_proper_host_is_passed() throws Exception {
		DiscoveryClientHostLocator hostLocator = hostLocator(serviceInstanceWithValidHost());

		Host host = hostLocator.locate(null);

		then(host.getServiceName()).isEqualTo("serviceId");
		then(host.getPort()).isEqualTo((short)8_000);
		then(host.getIpv4()).isEqualTo(InetUtils.getIpAddressAsInt("localhost"));
	}

	@Test
	public void should_override_the_service_name_from_properties() throws Exception {
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		zipkinProperties.getService().setName("foo");
		DiscoveryClientHostLocator hostLocator = new DiscoveryClientHostLocator(serviceInstanceWithValidHost(), zipkinProperties);

		Host host = hostLocator.locate(null);

		then(host.getServiceName()).isEqualTo("foo");
		then(host.getPort()).isEqualTo((short)8_000);
		then(host.getIpv4()).isEqualTo(InetUtils.getIpAddressAsInt("localhost"));
	}

	private ServiceInstance serviceInstanceWithInvalidHost() {
		return new ServiceInstance() {
			@Override public String getServiceId() {
				return "serviceId";
			}

			@Override public String getHost() {
				throw new RuntimeException();
			}

			@Override public int getPort() {
				return 8000;
			}

			@Override public boolean isSecure() {
				return false;
			}

			@Override public URI getUri() {
				return null;
			}

			@Override public Map<String, String> getMetadata() {
				return null;
			}
		};
	}

	private ServiceInstance serviceInstanceWithValidHost() {
		return new ServiceInstance() {
			@Override public String getServiceId() {
				return "serviceId";
			}

			@Override public String getHost() {
				return "localhost";
			}

			@Override public int getPort() {
				return 8000;
			}

			@Override public boolean isSecure() {
				return false;
			}

			@Override public URI getUri() {
				return null;
			}

			@Override public Map<String, String> getMetadata() {
				return null;
			}
		};
	}
}