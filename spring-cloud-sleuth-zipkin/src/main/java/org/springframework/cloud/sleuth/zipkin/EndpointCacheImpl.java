package org.springframework.cloud.sleuth.zipkin;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.util.ObjectUtils;

import zipkin.Endpoint;

/**
 * Caches or not, depending on {@link ZipkinProperties#localEndpointCachingEnabled}.
 *
 * @author Marcin Wielgus
 * @since 1.1.0
 */
class EndpointCacheImpl implements EndpointCache {

	private final AtomicReference<CachedEntpoint> cachedEndpoint = new AtomicReference<>();
	private ZipkinProperties zipkinProperties;

	public EndpointCacheImpl(ZipkinProperties zipkinProperties) {
		this.zipkinProperties = zipkinProperties;
	}

	private boolean isCachingEnabled() {
		return this.zipkinProperties.isLocalEndpointCachingEnabled();
	}

	public Endpoint getEndpoint(EndpointFactory factory, Object... keys) {
		if (isCachingEnabled()) {
			return updateAndGet(factory, keys).getEndpoint();
		}
		else {
			return factory.create();
		}

	}

	/**
	 * Copied from 1.8 AtomicReference#updateAndGet
	 * @param factory
	 * @param keys
	 * @return
	 */
	private CachedEntpoint updateAndGet(EndpointFactory factory, Object... keys) {
		CachedEntpoint prev, next;
		do {
			prev = this.cachedEndpoint.get();
			if (prev == null || !prev.matches(keys)) {
				next = new CachedEntpoint(factory.create(), keys);
			}
			else {
				next = prev;
			}
		}
		while (!this.cachedEndpoint.compareAndSet(prev, next));
		return next;
	}

	private static final class CachedEntpoint {
		private final Endpoint endpoint;
		private final Object[] keys;

		public CachedEntpoint(Endpoint endpoint, Object[] keys) {
			this.endpoint = endpoint;
			this.keys = keys;
		}

		boolean matches(Object[] keys) {
			return ObjectUtils.nullSafeEquals(keys, this.keys);
		}

		public Endpoint getEndpoint() {
			return this.endpoint;
		}
	}

}
