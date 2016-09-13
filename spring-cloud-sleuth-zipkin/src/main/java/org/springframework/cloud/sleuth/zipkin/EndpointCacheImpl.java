package org.springframework.cloud.sleuth.zipkin;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.util.ObjectUtils;

import zipkin.Endpoint;

/**
 * Caches
 *
 * @author Marcin Wielgus
 * @since 1.1.0
 */
class EndpointCacheImpl implements EndpointCache {

	private final AtomicReference<CachedEntpoint> cachedEndpoint = new AtomicReference<>();

	public Endpoint getEndpoint(EndpointFactory factory, ServiceInstance instance) {
		return updateAndGet(factory, instance).getEndpoint();
	}

	/**
	 * Copied from 1.8 AtomicReference#updateAndGet
	 * @param factory
	 * @param instance
	 * @return
	 */
	private CachedEntpoint updateAndGet(EndpointFactory factory,
			ServiceInstance instance) {
		CachedEntpoint prev, next;
		do {
			prev = this.cachedEndpoint.get();

			if (prev == null || !prev.matches(getHost(instance), instance.getPort(),
					instance.getServiceId())) {
				next = new CachedEntpoint(factory.create(), getHost(instance),
						instance.getPort(), instance.getServiceId());
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

		public CachedEntpoint(Endpoint endpoint, Object... keys) {
			this.endpoint = endpoint;
			this.keys = keys;
		}

		boolean matches(Object... keys) {
			return ObjectUtils.nullSafeEquals(keys, this.keys);
		}

		public Endpoint getEndpoint() {
			return this.endpoint;
		}
	}

	private static String getHost(ServiceInstance instance) {
		try {
			return instance.getHost();
		}
		catch (Exception e) {
			return null;
		}
	}

}
