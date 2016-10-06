package org.springframework.cloud.sleuth.zipkin;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import zipkin.Endpoint;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class FallbackHavingEndpointLocatorTests {

	@Mock DiscoveryClientEndpointLocator discoveryClientEndpointLocator;
	@Mock ServerPropertiesEndpointLocator serverPropertiesEndpointLocator;
	Endpoint expectedEndpoint = Endpoint.builder()
			.serviceName("my-tomcat").ipv4(127 << 24 | 1).port(8080).build();

	@Test
	public void should_use_system_property_locator_if_discovery_client_locator_is_not_present() {
		given(this.serverPropertiesEndpointLocator.local()).willReturn(this.expectedEndpoint);
		FallbackHavingEndpointLocator sut = new FallbackHavingEndpointLocator(null,
				this.serverPropertiesEndpointLocator);

		Endpoint endpoint = sut.local();

		then(endpoint).isSameAs(this.expectedEndpoint);
	}

	@Test
	public void should_use_system_property_locator_if_discovery_client_locator_throws_an_exception() {
		given(this.discoveryClientEndpointLocator.local()).willThrow(new RuntimeException());
		given(this.serverPropertiesEndpointLocator.local()).willReturn(this.expectedEndpoint);
		FallbackHavingEndpointLocator sut = new FallbackHavingEndpointLocator(this.discoveryClientEndpointLocator,
				this.serverPropertiesEndpointLocator);

		Endpoint endpoint = sut.local();

		then(endpoint).isSameAs(this.expectedEndpoint);
	}

	@Test
	public void should_use_discovery_client_locator_by_default() {
		given(this.discoveryClientEndpointLocator.local()).willReturn(this.expectedEndpoint);
		given(this.serverPropertiesEndpointLocator.local()).willThrow(new RuntimeException());
		FallbackHavingEndpointLocator sut = new FallbackHavingEndpointLocator(this.discoveryClientEndpointLocator,
				this.serverPropertiesEndpointLocator);

		Endpoint endpoint = sut.local();

		then(endpoint).isSameAs(this.expectedEndpoint);
	}
}