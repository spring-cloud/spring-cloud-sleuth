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

package org.springframework.cloud.sleuth.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth Tracer properties.
 *
 * @author Marcin Grzejszczak
 * @since 3.0
 */
@ConfigurationProperties("spring.sleuth.tracer")
public class SleuthTracerProperties {

	/**
	 * Set which tracer implementation should be picked.
	 */
	private TracerMode mode = TracerMode.AUTO;

	public TracerMode getMode() {
		return this.mode;
	}

	public void setMode(TracerMode mode) {
		this.mode = mode;
	}

	/**
	 * Tracer implementation modes.
	 */
	public enum TracerMode {

		/**
		 * Automatically picks the tracer implementation.
		 */
		AUTO,

		/**
		 * Picks the OpenZipkin Brave tracer implementation.
		 */
		BRAVE,

		/**
		 * Picks the OpenTelemetry tracer implementation.
		 */
		OTEL

	}

}
