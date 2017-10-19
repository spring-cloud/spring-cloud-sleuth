package org.springframework.cloud.sleuth.zipkin2;

import java.net.URI;

/**
 * Load balancing strategy for picking a Zipkin instance
 *
 * @author Marcin Grzejszczak
 * @since 1.3.0
 */
public interface ZipkinLoadBalancer {

	/**
	 * Returns a concrete {@link URI} of a Zipkin instance.
	 *
	 * @return {@link URI} of the picked instance
	 */
	URI instance();
}
