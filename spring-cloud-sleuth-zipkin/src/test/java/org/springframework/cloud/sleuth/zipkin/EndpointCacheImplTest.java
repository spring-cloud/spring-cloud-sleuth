package org.springframework.cloud.sleuth.zipkin;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.zipkin.DiscoveryClientEndpointLocatorTest.serviceInstanceWithHost;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.commons.util.InetUtils;

import zipkin.Endpoint;

/**
 * @author Marcin Wielgus
 */
@RunWith(MockitoJUnitRunner.class)
public class EndpointCacheImplTest {

	@InjectMocks
	EndpointCacheImpl discoveryClientEndpointLocator;

	@Test
	public void should_return_cached_instance_when_hosts_are_equal() {

		Endpoint endpoint1 = discoveryClientEndpointLocator.getEndpoint(
				() -> createSampleEndpoint(), serviceInstanceWithHost("localhost"));
		Endpoint endpoint2 = discoveryClientEndpointLocator.getEndpoint(
				() -> createSampleEndpoint(), serviceInstanceWithHost("localhost"));

		then(endpoint1).isSameAs(endpoint2);

	}

	@Test
	public void should_return_new_instance_when_host_is_changed() {

		Endpoint endpoint1 = discoveryClientEndpointLocator.getEndpoint(
				() -> createSampleEndpoint(), serviceInstanceWithHost("localhost"));
		Endpoint endpoint2 = discoveryClientEndpointLocator.getEndpoint(
				() -> createSampleEndpoint(), serviceInstanceWithHost("127.0.0.1"));

		then(endpoint1).isNotSameAs(endpoint2);

	}

	@Test
	public void should_return_new_instance_when_host_gets_missing() {

		Endpoint endpoint1 = discoveryClientEndpointLocator.getEndpoint(
				() -> createSampleEndpoint(), serviceInstanceWithHost("localhost"));
		Endpoint endpoint2 = discoveryClientEndpointLocator
				.getEndpoint(() -> createSampleEndpoint(), serviceInstanceWithHost(null));

		then(endpoint1).isNotSameAs(endpoint2);

	}

	private Endpoint createSampleEndpoint() {
		return Endpoint.create("service", InetUtils.getIpAddressAsInt("localhost"), 1234);
	}

}