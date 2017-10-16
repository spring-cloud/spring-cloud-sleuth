package org.springframework.cloud.sleuth.zipkin;

import java.net.URI;
import java.util.List;

import org.springframework.cloud.client.ServiceInstance;

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
	 * @param instances - Zipkin instances registered in service discovery
	 * @return {@link URI} of the picked instance
	 */
	URI instance(List<ServiceInstance> instances);
}
