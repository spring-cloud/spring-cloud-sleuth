package org.springframework.cloud.sleuth.zipkin;

import java.net.URI;

/**
 * Load balancing strategy for picking a Zipkin instance
 *
 * @author Marcin Grzejszczak
 * @since 1.3.0
 * @deprecated Please use spring-cloud-sleuth-zipkin2 to report spans to Zipkin
 */
@Deprecated
public interface ZipkinLoadBalancer {

	/**
	 * Returns a concrete {@link URI} of a Zipkin instance.
	 *
	 * @return {@link URI} of the picked instance
	 */
	URI instance();
}
