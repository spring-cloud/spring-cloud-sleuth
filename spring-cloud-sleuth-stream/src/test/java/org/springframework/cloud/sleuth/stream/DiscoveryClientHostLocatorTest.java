/*
 * Copyright 2013-2016 the original author or authors.
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

import java.net.URI;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.InetUtils;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

/**
 * @author Marcin Grzejszczak
 */
public class DiscoveryClientHostLocatorTest {
	DiscoveryClient discoveryClient = Mockito.mock(DiscoveryClient.class);
	DiscoveryClientHostLocator discoveryClientHostLocator =
			new DiscoveryClientHostLocator(this.discoveryClient, new ZipkinProperties());

	@Test(expected = IllegalArgumentException.class)
	public void should_throw_exception_when_no_discovery_client_is_present() throws Exception {
		new DiscoveryClientHostLocator(null, new ZipkinProperties());
	}

	@Test
	public void should_create_Host_with_0_ip_when_exception_occurs_on_resolving_host() throws Exception {
		given(this.discoveryClient.getLocalServiceInstance()).willReturn(serviceInstanceWithInvalidHost());

		Host host = this.discoveryClientHostLocator.locate(null);

		then(host.getServiceName()).isEqualTo("serviceId");
		then(host.getPort()).isEqualTo((short)8_000);
		then(host.getIpv4()).isEqualTo(0);
	}

	@Test
	public void should_create_valid_Host_when_proper_host_is_passed() throws Exception {
		given(this.discoveryClient.getLocalServiceInstance()).willReturn(serviceInstanceWithValidHost());

		Host host = this.discoveryClientHostLocator.locate(null);

		then(host.getServiceName()).isEqualTo("serviceId");
		then(host.getPort()).isEqualTo((short)8_000);
		then(host.getIpv4()).isEqualTo(InetUtils.getIpAddressAsInt("localhost"));
	}

	@Test
	public void should_override_the_service_name_from_properties() throws Exception {
		given(this.discoveryClient.getLocalServiceInstance()).willReturn(serviceInstanceWithValidHost());
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		zipkinProperties.getService().setName("foo");
		this.discoveryClientHostLocator = new DiscoveryClientHostLocator(this.discoveryClient, zipkinProperties);

		Host host = this.discoveryClientHostLocator.locate(null);

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