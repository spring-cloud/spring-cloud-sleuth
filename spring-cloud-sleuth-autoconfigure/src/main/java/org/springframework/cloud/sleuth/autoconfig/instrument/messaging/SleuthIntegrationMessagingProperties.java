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

package org.springframework.cloud.sleuth.autoconfig.instrument.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties for Spring Integration messaging.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@ConfigurationProperties("spring.sleuth.integration")
public class SleuthIntegrationMessagingProperties {

	/**
	 * An array of patterns against which channel names will be matched.
	 * @see org.springframework.integration.config.GlobalChannelInterceptor#patterns()
	 * Defaults to any channel name not matching the Hystrix Stream and functional Stream
	 * channel names.
	 */
	private String[] patterns = new String[] { "!hystrixStreamOutput*", "*", "!channel*" };

	/**
	 * Enable Spring Integration sleuth instrumentation.
	 */
	private boolean enabled;

	public String[] getPatterns() {
		return this.patterns;
	}

	public void setPatterns(String[] patterns) {
		this.patterns = patterns;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
