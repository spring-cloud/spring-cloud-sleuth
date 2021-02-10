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

package org.springframework.cloud.sleuth.instrument.reactor;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth Reactor settings.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.2
 */
@ConfigurationProperties("spring.sleuth.reactor")
// TODO: Hide in 3.x, if it isn't already deleted
public class SleuthReactorProperties {

	/**
	 * When true enables instrumentation for reactor.
	 */
	private boolean enabled = true;

	/**
	 * When true uses the new decorate hooks feature from Project Reactor.
	 */
	private boolean decorateHooks = true;

	/**
	 * When true decorates on each operator, will be less performing, but logging will
	 * always contain the tracing entries in each operator. When false decorates on last
	 * operator, will be more performing, but logging might not always contain the tracing
	 * entries.
	 */
	private boolean decorateOnEach = true;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isDecorateHooks() {
		return this.decorateHooks;
	}

	public void setDecorateHooks(boolean decorateHooks) {
		this.decorateHooks = decorateHooks;
	}

	public boolean isDecorateOnEach() {
		return this.decorateOnEach;
	}

	public void setDecorateOnEach(boolean decorateOnEach) {
		this.decorateOnEach = decorateOnEach;
	}

}
