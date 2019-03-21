/*
 * Copyright 2013-2015 the original author or authors.
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

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables a {@link Slf4jSpanLogger} that prints tracing information in the logs.
 * <p>
 * Note: this is only available for Slf4j
 *
 * @author Spencer Gibb
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(value="spring.sleuth.enabled", matchIfMissing=true)
public class SleuthLogAutoConfiguration {

	@Configuration
	@ConditionalOnClass(MDC.class)
	@EnableConfigurationProperties(SleuthSlf4jProperties.class)
	protected static class Slf4jConfiguration {

		@Bean
		@ConditionalOnProperty(value = "spring.sleuth.log.slf4j.enabled", matchIfMissing = true)
		@ConditionalOnMissingBean
		public SpanLogger slf4jSpanLogger(SleuthSlf4jProperties sleuthSlf4jProperties) {
			// Sets up MDC entries X-B3-TraceId and X-B3-SpanId
			return new Slf4jSpanLogger(sleuthSlf4jProperties.getNameSkipPattern());
		}

		@Bean
		@ConditionalOnProperty(value = "spring.sleuth.log.slf4j.enabled", havingValue = "false")
		@ConditionalOnMissingBean
		public SpanLogger noOpSlf4jSpanLogger() {
			return new NoOpSpanLogger();
		}
	}

	@Bean
	@ConditionalOnMissingClass("org.slf4j.MDC")
	@ConditionalOnMissingBean
	public SpanLogger defaultLoggedSpansHandler() {
		return new NoOpSpanLogger();
	}
}
