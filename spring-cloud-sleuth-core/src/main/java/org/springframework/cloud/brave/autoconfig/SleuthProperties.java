/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.brave.autoconfig;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth settings
 *
 * @since 1.0.11
 */
@ConfigurationProperties("spring.sleuth")
public class SleuthProperties {

	private boolean enabled = true;

	/**
	 * List of baggage key names that should be propagated out of process
	 */
	private List<String> baggageKeys = new ArrayList<>();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public List<String> getBaggageKeys() {
		return this.baggageKeys;
	}

	public void setBaggageKeys(List<String> baggageKeys) {
		this.baggageKeys = baggageKeys;
	}
}
