package org.springframework.cloud.sleuth.zipkin;

import java.net.URI;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.sleuth.zipkin.DiscoveryClientEndpointLocator.NoServiceInstanceAvailableException;

import zipkin.Endpoint;

import static org.assertj.core.api.Assertions.in;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

/**
 * @author Marcin Wielgus
 */
@RunWith(MockitoJUnitRunner.class)
public class EndpointCacheImplTest {
	@Mock
	ZipkinProperties zipkinProperties;
	@InjectMocks
	EndpointCacheImpl discoveryClientEndpointLocator;

	@Test
	public void should_always_return_new_instance_when_localEndpointCachingEnabled_is_false() {
		given(zipkinProperties.isLocalEndpointCachingEnabled()).willReturn(false);

		Endpoint endpoint1 = discoveryClientEndpointLocator
				.getEndpoint(() -> createSampleEndpoint(), "key1", 123);
		Endpoint endpoint2 = discoveryClientEndpointLocator
				.getEndpoint(() -> createSampleEndpoint(), "key1", 123);

		then(endpoint1).isNotSameAs(endpoint2);

	}

	@Test
	public void should_return_cached_instance_when_localEndpointCachingEnabled_is_true() {
		given(zipkinProperties.isLocalEndpointCachingEnabled()).willReturn(true);

		Endpoint endpoint1 = discoveryClientEndpointLocator
				.getEndpoint(() -> createSampleEndpoint(), "key1", 123);
		Endpoint endpoint2 = discoveryClientEndpointLocator
				.getEndpoint(() -> createSampleEndpoint(), "key1", 123);

		then(endpoint1).isSameAs(endpoint2);

	}

	@Test
	public void should_return_new_instance_when_caching_keys_are_different() {
		given(zipkinProperties.isLocalEndpointCachingEnabled()).willReturn(true);

		Endpoint endpoint1 = discoveryClientEndpointLocator
				.getEndpoint(() -> createSampleEndpoint(), "key1", 123);
		Endpoint endpoint2 = discoveryClientEndpointLocator
				.getEndpoint(() -> createSampleEndpoint(), "key2", 123);

		then(endpoint1).isNotSameAs(endpoint2);

	}

	@Test
	public void should_return_new_instance_when_caching_keys_are_missing() {
		given(zipkinProperties.isLocalEndpointCachingEnabled()).willReturn(true);

		Endpoint endpoint1 = discoveryClientEndpointLocator
				.getEndpoint(() -> createSampleEndpoint(), "key1", 123);
		Endpoint endpoint2 = discoveryClientEndpointLocator
				.getEndpoint(() -> createSampleEndpoint(), "key1");

		then(endpoint1).isNotSameAs(endpoint2);

	}

	private Endpoint createSampleEndpoint() {
		return Endpoint.create("service", InetUtils.getIpAddressAsInt("localhost"), 1234);
	}
}