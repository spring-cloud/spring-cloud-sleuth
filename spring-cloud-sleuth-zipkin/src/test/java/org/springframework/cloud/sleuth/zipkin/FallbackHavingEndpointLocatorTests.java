package org.springframework.cloud.sleuth.zipkin;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Endpoint;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(MockitoJUnitRunner.class)
public class FallbackHavingEndpointLocatorTests {

	@Mock DiscoveryClientEndpointLocator discoveryClientEndpointLocator;
	@Mock ServerPropertiesEndpointLocator serverPropertiesEndpointLocator;
	Endpoint expectedEndpoint = Endpoint.create("my-tomcat", 127 << 24 | 1, 8080);

	@Test
	public void should_use_system_property_locator_if_discovery_client_locator_is_not_present() {
		BDDMockito.given(this.serverPropertiesEndpointLocator.local()).willReturn(this.expectedEndpoint);
		FallbackHavingEndpointLocator sut = new FallbackHavingEndpointLocator(null,
				this.serverPropertiesEndpointLocator);

		Endpoint endpoint = sut.local();

		then(endpoint).isSameAs(this.expectedEndpoint);
	}

	@Test
	public void should_use_system_property_locator_if_discovery_client_locator_throws_an_exception() {
		BDDMockito.given(this.discoveryClientEndpointLocator.local()).willThrow(new RuntimeException());
		BDDMockito.given(this.serverPropertiesEndpointLocator.local()).willReturn(this.expectedEndpoint);
		FallbackHavingEndpointLocator sut = new FallbackHavingEndpointLocator(this.discoveryClientEndpointLocator,
				this.serverPropertiesEndpointLocator);

		Endpoint endpoint = sut.local();

		then(endpoint).isSameAs(this.expectedEndpoint);
	}

	@Test
	public void should_use_discovery_client_locator_by_default() {
		BDDMockito.given(this.discoveryClientEndpointLocator.local()).willReturn(this.expectedEndpoint);
		BDDMockito.given(this.serverPropertiesEndpointLocator.local()).willThrow(new RuntimeException());
		FallbackHavingEndpointLocator sut = new FallbackHavingEndpointLocator(this.discoveryClientEndpointLocator,
				this.serverPropertiesEndpointLocator);

		Endpoint endpoint = sut.local();

		then(endpoint).isSameAs(this.expectedEndpoint);
	}
}