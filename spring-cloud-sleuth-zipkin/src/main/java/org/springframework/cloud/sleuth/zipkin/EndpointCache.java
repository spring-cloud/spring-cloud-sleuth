package org.springframework.cloud.sleuth.zipkin;

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
	 * for later usage along with provided <code>keys</code>. Upon next calls will check
	 * if <code>keys</code> are equal to previously stored. If they are previous instance
	 * of Endpoint will be returned, otherwise it will recreate Endpoint using
	 * <code>factory</code>.
	 * @param factory
	 * @param keys
	 * @return
	 */
	Endpoint getEndpoint(EndpointCacheImpl.EndpointFactory factory, Object... keys);

	interface EndpointFactory {
		Endpoint create();
	}
}
