/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignContext;

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
