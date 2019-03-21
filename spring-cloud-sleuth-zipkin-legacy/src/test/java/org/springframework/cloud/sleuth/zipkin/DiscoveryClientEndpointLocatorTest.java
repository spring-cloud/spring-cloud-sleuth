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

package org.springframework.cloud.sleuth.zipkin;

import java.net.URI;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.sleuth.zipkin.DiscoveryClientEndpointLocator.NoServiceInstanceAvailableException;

import static org.assertj.core.api.BDDAssertions.then;

import zipkin.Endpoint;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class DiscoveryClientEndpointLocatorTest {

	@Test(expected = NoServiceInstanceAvailableException.class)
	public void should_throw_exception_when_no_instances_are_available() throws Exception {
		DiscoveryClientEndpointLocator endpointLocator = endpointLocator(null);
		endpointLocator.local();
	}

	private DiscoveryClientEndpointLocator endpointLocator(ServiceInstance serviceInstance) {
		return endpointLocator(serviceInstance, new ZipkinProperties());
	}

	private DiscoveryClientEndpointLocator endpointLocator(ServiceInstance serviceInstance, ZipkinProperties zipkinProperties) {
		return new DiscoveryClientEndpointLocator(serviceInstance, zipkinProperties);
	}

	@Test
	public void should_create_endpoint_with_0_ip_when_exception_occurs_on_resolving_host() throws Exception {
		DiscoveryClientEndpointLocator endpointLocator = endpointLocator(serviceInstanceWithInvalidHost());

		Endpoint local = endpointLocator.local();

		then(local.serviceName).isEqualTo("serviceid");
		then(local.port).isEqualTo((short)8_000);
		then(local.ipv4).isEqualTo(0);
	}

	@Test
	public void should_create_valid_endpoint_when_proper_host_is_passed() throws Exception {
		DiscoveryClientEndpointLocator endpointLocator = endpointLocator(serviceInstanceWithValidHost());

		Endpoint local = endpointLocator.local();

		then(local.serviceName).isEqualTo("serviceid");
		then(local.port).isEqualTo((short)8_000);
		then(local.ipv4).isEqualTo(InetUtils.getIpAddressAsInt("localhost"));
	}

	@Test
	public void should_create_endpoint_with_overridden_name() throws Exception {
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		zipkinProperties.getService().setName("foo");
		DiscoveryClientEndpointLocator locator = endpointLocator(serviceInstanceWithValidHost(), zipkinProperties);

		Endpoint local = locator.local();

		then(local.serviceName).isEqualTo("foo");
		then(local.port).isEqualTo((short)8_000);
		then(local.ipv4).isEqualTo(InetUtils.getIpAddressAsInt("localhost"));
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