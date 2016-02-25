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

package org.springframework.cloud.sleuth.log;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables a {@link Slf4jSpanListener} that prints tracing information in the logs.
 * <p>
 * Note: this is only available for Slf4j
 *
 * @author Spencer Gibb
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
public class SleuthLogAutoConfiguration {

	@Configuration
	@ConditionalOnClass(MDC.class)
	protected static class Slf4jConfiguration {
		/**
		 * Name pattern for which span should not be printed in the logs
		 */
		@Value("${spring.sleuth.log.slf4j.nameSkipPattern:}")
		private String nameSkipPattern;

		@Bean
		@ConditionalOnProperty(value = "spring.sleuth.log.slf4j.enabled", matchIfMissing = true)
		public Slf4jSpanListener slf4jSpanStartedListener() {
			// Sets up MDC entries X-Trace-Id and X-Span-Id
			return new Slf4jSpanListener(this.nameSkipPattern);
		}
	}
}
