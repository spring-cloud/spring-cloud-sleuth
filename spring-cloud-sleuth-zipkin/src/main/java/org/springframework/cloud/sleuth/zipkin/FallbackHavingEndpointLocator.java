package org.springframework.cloud.sleuth.zipkin;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Endpoint;

/**
 * Endpoint locator that will try to call an endpoint via Discovery Client
 * and will fallback to Server Properties if an exception is thrown
 */
public class FallbackHavingEndpointLocator implements EndpointLocator {

	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DiscoveryClientEndpointLocator discoveryClientEndpointLocator;
	private final ServerPropertiesEndpointLocator serverPropertiesEndpointLocator;

	public FallbackHavingEndpointLocator(DiscoveryClientEndpointLocator discoveryClientEndpointLocator,
										ServerPropertiesEndpointLocator serverPropertiesEndpointLocator) {
		this.discoveryClientEndpointLocator = discoveryClientEndpointLocator;
		this.serverPropertiesEndpointLocator = serverPropertiesEndpointLocator;
	}

	@Override
	public Endpoint local() {
		if (this.discoveryClientEndpointLocator == null) {
			return this.serverPropertiesEndpointLocator.local();
		}
		try {
			return this.discoveryClientEndpointLocator.local();
		} catch (Exception e) {
			log.warn("Exception occurred while trying to fetch the Zipkin process endpoint. Falling back to server properties endpoint locator.", e);
			return this.serverPropertiesEndpointLocator.local();
		}
	}
}
