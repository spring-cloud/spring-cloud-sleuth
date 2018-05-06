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

import java.util.regex.Pattern;

import brave.Tracing;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that sets up common building blocks for both reactive
 * and servlet based web application.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.web.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracing.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@EnableConfigurationProperties(SleuthWebProperties.class)
public class TraceWebAutoConfiguration {

	@Configuration
	@ConditionalOnClass(ManagementServerProperties.class)
	@ConditionalOnMissingBean(SkipPatternProvider.class)
	@EnableConfigurationProperties(SleuthWebProperties.class)
	protected static class SkipPatternProviderConfig {

		@Bean
		@ConditionalOnBean(ManagementServerProperties.class)
		public SkipPatternProvider skipPatternForManagementServerProperties(
				final ManagementServerProperties managementServerProperties,
				final SleuthWebProperties sleuthWebProperties) {
			return new SkipPatternProvider() {
				@Override
				public Pattern skipPattern() {
					return getPatternForManagementServerProperties(
							managementServerProperties,
							sleuthWebProperties);
				}
			};
		}

		/**
		 * Sets or appends {@link ManagementServerProperties#getServlet()#getContextPath()} to the skip
		 * pattern. If neither is available then sets the default one
		 */
		static Pattern getPatternForManagementServerProperties(
				ManagementServerProperties managementServerProperties,
				SleuthWebProperties sleuthWebProperties) {
			String skipPattern = sleuthWebProperties.getSkipPattern();
			String additionalSkipPattern = sleuthWebProperties.getAdditionalSkipPattern();
			String contextPath = managementServerProperties.getServlet().getContextPath();

			if (StringUtils.hasText(skipPattern) && StringUtils.hasText(contextPath)) {
				return Pattern.compile(combinedPattern(skipPattern + "|" + contextPath + ".*", additionalSkipPattern));
			}
			else if (StringUtils.hasText(contextPath)) {
				return Pattern.compile(combinedPattern(contextPath + ".*", additionalSkipPattern));
			}
			return defaultSkipPattern(skipPattern, additionalSkipPattern);
		}

		@Bean
		@ConditionalOnMissingBean(ManagementServerProperties.class)
		public SkipPatternProvider defaultSkipPatternBeanIfManagementServerPropsArePresent(SleuthWebProperties sleuthWebProperties) {
			return defaultSkipPatternProvider(sleuthWebProperties.getSkipPattern(),
					sleuthWebProperties.getAdditionalSkipPattern());
		}
	}

	@Bean
	@ConditionalOnMissingClass("org.springframework.boot.actuate.autoconfigure.ManagementServerProperties")
	@ConditionalOnMissingBean(
			SkipPatternProvider.class)
	public SkipPatternProvider defaultSkipPatternBean(SleuthWebProperties sleuthWebProperties) {
		return defaultSkipPatternProvider(sleuthWebProperties.getSkipPattern(),
				sleuthWebProperties.getAdditionalSkipPattern());
	}

	private static SkipPatternProvider defaultSkipPatternProvider(
			final String skipPattern, final String additionalSkipPattern) {
		return () -> defaultSkipPattern(skipPattern, additionalSkipPattern);
	}

	private static Pattern defaultSkipPattern(String skipPattern, String additionalSkipPattern) {
		return Pattern.compile(combinedPattern(skipPattern, additionalSkipPattern));
	}

	private static String combinedPattern(String skipPattern, String additionalSkipPattern) {
		String pattern = skipPattern;
		if (!StringUtils.hasText(skipPattern)) {
			pattern = SleuthWebProperties.DEFAULT_SKIP_PATTERN;
		}
		if (StringUtils.hasText(additionalSkipPattern)) {
			return pattern + "|" + additionalSkipPattern;
		}
		return pattern;
	}

}

