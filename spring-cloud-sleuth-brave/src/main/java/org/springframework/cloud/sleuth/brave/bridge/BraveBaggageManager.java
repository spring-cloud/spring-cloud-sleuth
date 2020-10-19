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

import org.springframework.cloud.sleuth.api.BaggageEntry;
import org.springframework.cloud.sleuth.api.BaggageManager;

/**
 * Brave implementation of a {@link BaggageManager}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class BraveBaggageManager implements BaggageManager, Closeable {

	private static Map<String, BaggageEntry> CACHE = new ConcurrentHashMap<>();

	@Override
	public Map<String, String> getAllBaggage() {
		return BaggageField.getAllValues();
	}

	@Override
	public BaggageEntry getBaggage(String name) {
		return createBaggage(name);
	}

	@Override
	public BaggageEntry createBaggage(String name) {
		return CACHE.computeIfAbsent(name, s -> new BraveBaggageEntry(BaggageField.create(s)));
	}

	@Override
	public void close() {
		CACHE.clear();
	}

}
