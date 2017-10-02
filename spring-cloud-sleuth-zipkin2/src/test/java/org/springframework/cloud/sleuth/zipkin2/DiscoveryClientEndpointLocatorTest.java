/*
 * Copyright 2013-2017 the original author or authors.
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

import zipkin2.Endpoint;

import java.net.URI;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.sleuth.zipkin2.DiscoveryClientEndpointLocator.NoServiceInstanceAvailableException;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class DiscoveryClientEndpointLocatorTest {

	@Mock DiscoveryClient discoveryClient;
	DiscoveryClientEndpointLocator discoveryClientEndpointLocator;

	@Before
	public void setup() {
		this.discoveryClientEndpointLocator = new DiscoveryClientEndpointLocator(this.discoveryClient, new ZipkinProperties());
	}

	@Test(expected = NoServiceInstanceAvailableException.class)
	public void should_throw_exception_when_no_instances_are_available() throws Exception {
		this.discoveryClientEndpointLocator.local();
	}

	@Test
	public void should_create_endpoint_with_0_ip_when_exception_occurs_on_resolving_host() throws Exception {
		given(this.discoveryClient.getLocalServiceInstance()).willReturn(serviceInstanceWithInvalidHost());

		Endpoint local = this.discoveryClientEndpointLocator.local();

		then(local.serviceName()).isEqualTo("serviceid");
		then(local.port()).isEqualTo(8_000);
		then(local.ipv4()).isNull();
	}

	@Test
	public void should_create_valid_endpoint_when_proper_host_is_passed() throws Exception {
		given(this.discoveryClient.getLocalServiceInstance()).willReturn(serviceInstanceWithValidHost());

		Endpoint local = this.discoveryClientEndpointLocator.local();

		then(local.serviceName()).isEqualTo("serviceid");
		then(local.port()).isEqualTo(8_000);
		then(local.ipv4()).isNull(); // localhost isn't an IP!
	}

	@Test
	public void should_create_endpoint_with_overridden_name() throws Exception {
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		zipkinProperties.getService().setName("foo");
		DiscoveryClientEndpointLocator locator = new DiscoveryClientEndpointLocator(this.discoveryClient, zipkinProperties);
		given(this.discoveryClient.getLocalServiceInstance()).willReturn(serviceInstanceWithValidHost());

		Endpoint local = locator.local();

		then(local.serviceName()).isEqualTo("foo");
		then(local.port()).isEqualTo(8_000);
		then(local.ipv4()).isNull(); // localhost isn't an IP!
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