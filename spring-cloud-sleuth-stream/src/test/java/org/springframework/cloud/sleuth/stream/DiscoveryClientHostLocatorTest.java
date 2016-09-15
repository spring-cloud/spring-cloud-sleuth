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

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.InetUtils;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class DiscoveryClientHostLocatorTest {
	@Mock
	DiscoveryClient discoveryClient;
	@Mock
	SleuthStreamProperties properties;
	@InjectMocks
	DiscoveryClientHostLocator discoveryClientHostLocator;

	@Test(expected = IllegalArgumentException.class)
	public void should_throw_exception_when_no_discovery_client_is_present()
			throws Exception {
		new DiscoveryClientHostLocator(null, new SleuthStreamProperties());
	}

	@Test
	public void should_create_Host_with_0_ip_when_exception_occurs_on_resolving_host()
			throws Exception {
		ServiceInstance serviceInstanceWithHost = serviceInstanceWithExceptionInGetHost();
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithHost);

		Host host = this.discoveryClientHostLocator.locate(null);

		then(host.getServiceName()).isEqualTo("serviceId");
		then(host.getPort()).isEqualTo((short) 8_000);
		then(host.getIpv4()).isEqualTo(0);
	}


	@Test
	public void should_create_Host_with_0_ip_when_host_is_unresolvable()
			throws Exception {
		ServiceInstance serviceInstanceWithHost = serviceInstanceWithHost(
				"_ invalid host name with space * and other funny stuff");
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithHost);

		Host host = this.discoveryClientHostLocator.locate(null);

		then(host.getServiceName()).isEqualTo("serviceId");
		then(host.getPort()).isEqualTo((short) 8_000);
		then(host.getIpv4()).isEqualTo(0);
	}

	@Test
	public void should_create_valid_Host_when_proper_host_is_passed() throws Exception {
		ServiceInstance serviceInstanceWithHost = serviceInstanceWithHost("localhost");
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithHost);

		Host host = this.discoveryClientHostLocator.locate(null);

		then(host.getServiceName()).isEqualTo("serviceId");
		then(host.getPort()).isEqualTo((short) 8_000);
		then(host.getIpv4()).isEqualTo(InetUtils.getIpAddressAsInt("localhost"));
	}

	@Test
	public void should_create_new_endpoint_when_caching_is_off() throws Exception {
		ServiceInstance serviceInstanceWithValidHost = serviceInstanceWithHost(
				"localhost");
		given(this.properties.isLocalEndpointCachingEnabled()).willReturn(false);
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithValidHost);

		Host host1 = this.discoveryClientHostLocator.locate(null);
		Host host2 = this.discoveryClientHostLocator.locate(null);

		then(host1.getServiceName()).isEqualTo("serviceId");
		then(host1.getPort()).isEqualTo((short) 8_000);
		then(host1.getIpv4()).isEqualTo(InetUtils.getIpAddressAsInt("localhost"));
		then(host1).isNotSameAs(host2);
	}

	@Test
	public void should_not_create_new_endpoint_when_caching_is_on() throws Exception {
		ServiceInstance serviceInstanceWithValidHost = serviceInstanceWithHost(
				"localhost");
		given(this.properties.isLocalEndpointCachingEnabled()).willReturn(true);
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithValidHost);

		Host endpoint1 = this.discoveryClientHostLocator.locate(null);
		Host endpoint2 = this.discoveryClientHostLocator.locate(null);

		then(endpoint1).isSameAs(endpoint2);
	}

	@Test
	public void should_create_new_endpoint_when_caching_is_on_and_host_changes()
			throws Exception {
		ServiceInstance serviceInstance1 = serviceInstanceWithHost("localhost");
		ServiceInstance serviceInstance2 = serviceInstanceWithHost("127.0.0.1");
		given(this.properties.isLocalEndpointCachingEnabled()).willReturn(true);
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstance1);

		Host endpoint1 = this.discoveryClientHostLocator.locate(null);

		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstance2);

		Host endpoint2 = this.discoveryClientHostLocator.locate(null);

		then(endpoint1).isNotSameAs(endpoint2);
	}

	/**
	 * Original test case asumed there is exception in getHost(), so I keep it but don't
	 * think it's required.
	 * @return
	 */
	private ServiceInstance serviceInstanceWithExceptionInGetHost() {
		ServiceInstance serviceInstanceMock = serviceInstanceMock();
		when(serviceInstanceMock.getHost()).thenThrow(new RuntimeException());
		return serviceInstanceMock;
	}

	static ServiceInstance serviceInstanceMock() {
		ServiceInstance mock = Mockito.mock(ServiceInstance.class);
		when(mock.getServiceId()).thenReturn("serviceId");
		when(mock.getPort()).thenReturn(8000);
		return mock;
	}

	static ServiceInstance serviceInstanceWithHost(String host) {
		ServiceInstance serviceInstanceMock = serviceInstanceMock();
		when(serviceInstanceMock.getHost()).thenReturn(host);
		return serviceInstanceMock;
	}
}