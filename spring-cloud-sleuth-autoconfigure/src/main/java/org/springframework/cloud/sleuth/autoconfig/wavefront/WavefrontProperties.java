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

package org.springframework.cloud.sleuth.autoconfig.wavefront;

import java.util.HashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Advanced configuration properties for Wavefront.
 *
 * @author Stephane Nicoll
 * @since 3.0.2
 */
@ConfigurationProperties("spring.sleuth.wavefront")
public class WavefrontProperties {

	/**
	 * Enables Wavefront tracing.
	 */
	private boolean enabled;

	/**
	 * Tags that should be associated with RED metrics. If the span has any of the
	 * specified tags, then those get reported to generated RED metrics.
	 */
	private Set<String> redMetricsCustomTagKeys = new HashSet<>();

	public Set<String> getRedMetricsCustomTagKeys() {
		return this.redMetricsCustomTagKeys;
	}

	public void setRedMetricsCustomTagKeys(Set<String> redMetricsCustomTagKeys) {
		this.redMetricsCustomTagKeys = redMetricsCustomTagKeys;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
