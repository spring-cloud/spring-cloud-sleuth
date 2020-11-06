/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.brave.bridge;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import brave.baggage.BaggageField;

import org.springframework.cloud.sleuth.api.BaggageInScope;
import org.springframework.cloud.sleuth.api.BaggageManager;
import org.springframework.cloud.sleuth.api.TraceContext;

/**
 * Brave implementation of a {@link BaggageManager}. Not implementing the interface
 * directly cause {@link BraveTracer} will already implement it.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class BraveBaggageManager implements Closeable {

	private static final Map<String, BaggageInScope> CACHE = new ConcurrentHashMap<>();

	public Map<String, String> getAllBaggage() {
		return BaggageField.getAllValues();
	}

	public BaggageInScope getBaggage(String name) {
		return createBaggage(name);
	}

	public BaggageInScope getBaggage(TraceContext traceContext, String name) {
		return new BraveBaggageInScope(BaggageField.getByName(BraveTraceContext.toBrave(traceContext), name));
	}

	public BaggageInScope createBaggage(String name) {
		return CACHE.computeIfAbsent(name, s -> new BraveBaggageInScope(BaggageField.create(s)));
	}

	@Override
	public void close() {
		CACHE.clear();
	}

}
