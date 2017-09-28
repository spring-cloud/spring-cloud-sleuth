/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.jms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for {@link org.springframework.jms.annotation.JmsListener} tracing.
 *
 * @author Stefan Zeller
 * @since 1.2.5
 */
@ConfigurationProperties("spring.sleuth.jms")
public class SleuthJmsListenerProperties {

	/**
	 * Enable tracing for {@link org.springframework.jms.annotation.JmsListener}.
	 */
	private boolean enabled = true;

	/**
	 * Pattern for the fully qualified name of a class that should be skipped.
	 */
	private String skipPattern = "";

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
