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

package org.springframework.cloud.sleuth.zipkin;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.sleuth.zipkin.DiscoveryClientEndpointLocator.NoServiceInstanceAvailableException;

import zipkin.Endpoint;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class DiscoveryClientEndpointLocatorTest {

	@Mock
	DiscoveryClient discoveryClient;
	@Mock
	ZipkinProperties zipkinProperties;

	@InjectMocks
	DiscoveryClientEndpointLocator discoveryClientEndpointLocator;

	@Test(expected = NoServiceInstanceAvailableException.class)
	public void should_throw_exception_when_no_instances_are_available()
			throws Exception {
		this.discoveryClientEndpointLocator.local();
	}

	@Test
	public void should_create_endpoint_with_0_ip_when_exception_occurs_on_resolving_host()
			throws Exception {
		ServiceInstance serviceInstanceWithInvalidHost = serviceInstanceWithHost(
				"_ invalid host name with space * and other funny stuff");
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithInvalidHost);

		Endpoint local = this.discoveryClientEndpointLocator.local();

		then(local.serviceName).isEqualTo("serviceid");
		then(local.port).isEqualTo((short) 8_000);
		then(local.ipv4).isEqualTo(0);
	}

	@Test
	public void should_create_endpoint_with_localhost_ip_when_exception_occurs_on_getting_host()
			throws Exception {
		ServiceInstance serviceInstanceWithExceptionInGetHost = serviceInstanceWithExceptionInGetHost();
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithExceptionInGetHost);

		Endpoint local = this.discoveryClientEndpointLocator.local();

		then(local.serviceName).isEqualTo("serviceid");
		then(local.port).isEqualTo((short) 8_000);
		then(local.ipv4).isEqualTo(InetUtils.getIpAddressAsInt("localhost"));
	}

	@Test
	public void should_create_endpoint_with_localhost_ip_when_null_host_is_passed()
			throws Exception {
		ServiceInstance serviceInstanceWithNullHost = serviceInstanceWithHost(null);
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithNullHost);

		Endpoint local = this.discoveryClientEndpointLocator.local();

		then(local.serviceName).isEqualTo("serviceid");
		then(local.port).isEqualTo((short) 8_000);
		then(local.ipv4).isEqualTo(InetUtils.getIpAddressAsInt("localhost"));
	}

	@Test
	public void should_create_valid_endpoint_when_proper_host_is_passed()
			throws Exception {
		ServiceInstance serviceInstanceWithValidHost = serviceInstanceWithHost(
				"localhost");
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithValidHost);

		Endpoint local = this.discoveryClientEndpointLocator.local();

		then(local.serviceName).isEqualTo("serviceid");
		then(local.port).isEqualTo((short) 8_000);
		then(local.ipv4).isEqualTo(InetUtils.getIpAddressAsInt("localhost"));
	}

	@Test
	public void should_return_different_instance_of_endpoint_when_service_instance_changed_hostname()
			throws Exception {
		ServiceInstance serviceInstanceWithLocalhost = serviceInstanceWithHost(
				"localhost");
		ServiceInstance serviceInstanceWithLocalIp = serviceInstanceWithHost("127.0.0.1");
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithLocalhost);

		Endpoint local1 = this.discoveryClientEndpointLocator.local();

		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithLocalIp);

		Endpoint local2 = this.discoveryClientEndpointLocator.local();

		then(local1).isNotSameAs(local2);

	}

	@Test
	public void should_create_new_endpoint_when_caching_is_off() throws Exception {
		ServiceInstance serviceInstanceWithValidHost = serviceInstanceWithHost(
				"localhost");
		given(this.zipkinProperties.isLocalEndpointCachingEnabled()).willReturn(false);
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithValidHost);

		Endpoint endpoint1 = this.discoveryClientEndpointLocator.local();
		Endpoint endpoint2 = this.discoveryClientEndpointLocator.local();

		then(endpoint1.serviceName).isEqualTo("serviceid");
		then(endpoint1.port).isEqualTo((short) 8_000);
		then(endpoint1.ipv4).isEqualTo(InetUtils.getIpAddressAsInt("localhost"));
		then(endpoint1).isNotSameAs(endpoint2);
	}

	@Test
	public void should_not_create_new_endpoint_when_caching_is_on() throws Exception {
		ServiceInstance serviceInstanceWithValidHost = serviceInstanceWithHost(
				"localhost");
		given(this.zipkinProperties.isLocalEndpointCachingEnabled()).willReturn(true);
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(serviceInstanceWithValidHost);

		Endpoint endpoint1 = this.discoveryClientEndpointLocator.local();
		Endpoint endpoint2 = this.discoveryClientEndpointLocator.local();

		then(endpoint1).isSameAs(endpoint2);
	}

	@Test
	public void should_create_new_endpoint_when_caching_is_on_and_host_changes() throws Exception {
		ServiceInstance host1 = serviceInstanceWithHost(
				"localhost");
		ServiceInstance host2 = serviceInstanceWithHost(
				"127.0.0.1");
		given(this.zipkinProperties.isLocalEndpointCachingEnabled()).willReturn(true);
		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(host1);

		Endpoint endpoint1 = this.discoveryClientEndpointLocator.local();

		given(this.discoveryClient.getLocalServiceInstance())
				.willReturn(host2);

		Endpoint endpoint2 = this.discoveryClientEndpointLocator.local();

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