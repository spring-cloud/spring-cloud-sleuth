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

package org.springframework.cloud.sleuth.api;

import java.util.Map;

/**
 * Manages {@link BaggageEntry} entries.
 *
 * @author OpenTelemetry Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface BaggageManager {

	/**
	 * @return mapping of all baggage entries
	 */
	Map<String, String> getAllBaggage();

	/**
	 * Retrieves {@link BaggageEntry} for the given name.
	 * @param name baggage name
	 * @return baggage or {@code null} if not present
	 */
	BaggageEntry getBaggage(String name);

	/**
	 * Creates a new {@link BaggageEntry} entry for the given name or returns an existing
	 * one if it's already present.
	 * @param name baggage name
	 * @return new or already created baggage
	 */
	BaggageEntry createBaggage(String name);

}
