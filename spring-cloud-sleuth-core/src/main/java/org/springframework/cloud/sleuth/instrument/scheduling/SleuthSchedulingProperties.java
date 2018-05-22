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

package org.springframework.cloud.sleuth.instrument.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for {@link org.springframework.scheduling.annotation.Scheduled} tracing
 *
 * @author Arthur Gavlyukovskiy
 * @since 1.0.12
 */
@ConfigurationProperties("spring.sleuth.scheduled")
public class SleuthSchedulingProperties {

	/**
	 * Enable tracing for {@link org.springframework.scheduling.annotation.Scheduled}.
	 */
	private boolean enabled = true;

	/**
	 * Pattern for the fully qualified name of a class that should be skipped.
	 */
	private String skipPattern = "org.springframework.cloud.netflix.hystrix.stream.HystrixStreamTask";

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getSkipPattern() {
		return this.skipPattern;
	}

	public void setSkipPattern(String skipPattern) {
		this.skipPattern = skipPattern;
	}
}
