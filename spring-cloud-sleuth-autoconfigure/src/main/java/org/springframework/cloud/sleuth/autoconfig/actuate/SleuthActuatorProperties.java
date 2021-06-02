/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.actuate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for Sleuth actuator.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
@ConfigurationProperties(prefix = "spring.sleuth.actuator")
public class SleuthActuatorProperties {

	/**
	 * Enable Sleuth actuator.
	 */
	private boolean enabled;

	/**
	 * Max capacity of the span queue.
	 */
	private int capacity = 10_000;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getCapacity() {
		return this.capacity;
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}
}
