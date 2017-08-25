package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.netflix.feign.FeignContext;

/**
 * Custom FeignContext that wraps beans in custom Feign configurations in their
 * tracing representations.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.1
 */
class TraceFeignContext extends FeignContext {

	private final TraceFeignObjectWrapper traceFeignObjectWrapper;
	private final FeignContext delegate;

	TraceFeignContext(TraceFeignObjectWrapper traceFeignObjectWrapper,
			FeignContext delegate) {
		this.traceFeignObjectWrapper = traceFeignObjectWrapper;
		this.delegate = delegate;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getInstance(String name, Class<T> type) {
		T object = this.delegate.getInstance(name, type);
		return (T) this.traceFeignObjectWrapper.wrap(object);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Map<String, T> getInstances(String name, Class<T> type) {
		Map<String, T> instances = this.delegate.getInstances(name, type);
		if (instances == null) {
			return null;
		}
		Map<String, T> convertedInstances = new HashMap<>();
		for (Map.Entry<String, T> entry : instances.entrySet()) {
			convertedInstances.put(entry.getKey(), (T) this.traceFeignObjectWrapper.wrap(entry.getValue()));
		}
		return convertedInstances;
	}

}
