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

package org.springframework.cloud.sleuth.log;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for slf4j.
 *
 * @author Arthur Gavlyukovskiy
 * @since 1.0.12
 */
@ConfigurationProperties("spring.sleuth.log.slf4j")
// TODO: Hide in 3.x, if it isn't already deleted
public class SleuthSlf4jProperties {

	private static final Log log = LogFactory.getLog(SleuthSlf4jProperties.class);

	/**
	 * Enable a {@link Slf4jScopeDecorator} that prints tracing information in the logs.
	 */
	private boolean enabled = true;

	/**
	 * A list of keys to be put from baggage to MDC.
	 * @deprecated use spring.sleuth.baggage.correlation-fields property
	 */
	@Deprecated
	private List<String> whitelistedMdcKeys = new ArrayList<>();

	public boolean isEnabled() {
		warning("spring.sleuth.log.slf4j.enabled",
				"spring.sleuth.baggage.correlation-enabled");
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public List<String> getWhitelistedMdcKeys() {
		return this.whitelistedMdcKeys;
	}

	public void setWhitelistedMdcKeys(List<String> whitelistedMdcKeys) {
		warning("spring.sleuth.log.slf4j.whitelisted-mdc-keys",
				"spring.sleuth.baggage.correlation-fields");
		this.whitelistedMdcKeys = whitelistedMdcKeys;
	}

	private void warning(String currentKey, String newKey) {
		log.warn("The [" + currentKey
				+ "] property is deprecated and is removed in the next major release version of Sleuth. Please use ["
				+ newKey + "]");
	}

}
