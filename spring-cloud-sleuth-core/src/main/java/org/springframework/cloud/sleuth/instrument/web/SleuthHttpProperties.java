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

package org.springframework.cloud.sleuth.instrument.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth HTTP settings
 *
 * @since 2.0.0
 */
@ConfigurationProperties("spring.sleuth.http")
public class SleuthHttpProperties {

	private boolean enabled = true;

	private Legacy legacy = new Legacy();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Legacy getLegacy() {
		return this.legacy;
	}

	public void setLegacy(Legacy legacy) {
		this.legacy = legacy;
	}

	/**
	 * Legacy Sleuth support. Related to the way headers are parsed and tags are set
	 */
	public static class Legacy {

		private boolean enabled = false;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}
}
