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

package org.springframework.cloud.sleuth.otel.propagation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth settings for OpenTelemetry.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@ConfigurationProperties("spring.sleuth.otel.propagation")
class OtelPropagationProperties {

	/**
	 * Type of propagation.
	 */
	// TODO: [OTEL] Allow this to be a list (same for Brave, maybe even push this property
	// up)
	private PropagationType type = PropagationType.B3;

	private SleuthBaggage sleuthBaggage = new SleuthBaggage();

	public PropagationType getType() {
		return this.type;
	}

	public void setType(PropagationType type) {
		this.type = type;
	}

	public SleuthBaggage getSleuthBaggage() {
		return this.sleuthBaggage;
	}

	public void setSleuthBaggage(SleuthBaggage sleuthBaggage) {
		this.sleuthBaggage = sleuthBaggage;
	}

	enum PropagationType {

		/**
		 * AWS propagation type.
		 */
		AWS,

		/**
		 * B3 propagation type.
		 */
		B3,

		/**
		 * Jaeger propagation type.
		 */
		JAEGER,

		/**
		 * Lightstep propagation type.
		 */
		OT_TRACER,

		/**
		 * Custom propagation type/
		 */
		CUSTOM

	}

	public static class SleuthBaggage {

		private boolean enabled = true;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

}
