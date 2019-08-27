/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.hystrix;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth Hystrix settings.
 *
 * @author Daniel Albuquerque
 */
@ConfigurationProperties("spring.sleuth.hystrix.strategy")
public class SleuthHystrixConcurrencyStrategyProperties {

	/**
	 * Enable custom HystrixConcurrencyStrategy that wraps all Callable instances into
	 * their Sleuth representative - the TraceCallable.
	 */
	private boolean enabled = true;

	/**
	 * When enabled the tracing information is passed to the Hystrix execution threads but
	 * spans are not created for each execution.
	 */
	private boolean passthrough = false;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isPassthrough() {
		return passthrough;
	}

	public void setPassthrough(boolean passthrough) {
		this.passthrough = passthrough;
	}

}
