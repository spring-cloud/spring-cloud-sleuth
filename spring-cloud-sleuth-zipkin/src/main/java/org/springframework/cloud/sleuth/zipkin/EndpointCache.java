package org.springframework.cloud.sleuth.zipkin;

import org.springframework.cloud.client.ServiceInstance;
import zipkin.Endpoint;

/**
 * Interface for caching located client endpoints.
 *
 * @author Marcin Wielgus
 * @since 1.1.0
 */
interface EndpointCache {

	/**
	 * When first called will create new Endpoint with <code>factory</code> and store it
	 * for later usage along with 'some data' from <code>instance</code>.
	 * Upon next calls will check if 'some data' of <code>instance</code> are equal
	 * to previously stored or not. If they are, previous instance
	 * of Endpoint will be returned, otherwise it will recreate Endpoint using
	 * <code>factory</code>.
	 * @param factory
	 * @param instance
	 * @return
	 */
	Endpoint getEndpoint(EndpointCache.EndpointFactory factory, ServiceInstance instance);

	interface EndpointFactory {
		Endpoint create();
	}
}
