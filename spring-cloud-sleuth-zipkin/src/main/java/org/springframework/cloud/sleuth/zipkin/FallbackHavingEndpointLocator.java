package org.springframework.cloud.sleuth.zipkin;

import lombok.extern.slf4j.Slf4j;
import zipkin.Endpoint;

/**
 * Endpoint locator that will try to call an endpoint via Discovery Client
 * and will fallback to Server Properties if an exception is thrown
 */
@Slf4j
public class FallbackHavingEndpointLocator implements EndpointLocator {

	private final DiscoveryClientEndpointLocator discoveryClientEndpointLocator;
	private final ServerPropertiesEndpointLocator serverPropertiesEndpointLocator;

	public FallbackHavingEndpointLocator(DiscoveryClientEndpointLocator discoveryClientEndpointLocator,
										 ServerPropertiesEndpointLocator serverPropertiesEndpointLocator) {
		this.discoveryClientEndpointLocator = discoveryClientEndpointLocator;
		this.serverPropertiesEndpointLocator = serverPropertiesEndpointLocator;
	}

	@Override
	public Endpoint local() {
		if (discoveryClientEndpointLocator == null) {
			return serverPropertiesEndpointLocator.local();
		}
		try {
			return discoveryClientEndpointLocator.local();
		} catch (Exception e) {
			log.warn("Exception occurred while trying to fetch the Zipkin process endpoint. Falling back to server properties endpoint locator.", e);
			return serverPropertiesEndpointLocator.local();
		}
	}
}
