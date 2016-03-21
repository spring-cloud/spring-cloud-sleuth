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
package org.springframework.cloud.sleuth.instrument.web;

import java.util.Random;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.ManagementServerProperties;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables tracing to HTTP requests.
 *
 * @author Tomasz Nurkewicz, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.web.enabled", matchIfMissing = true)
@ConditionalOnWebApplication
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@EnableConfigurationProperties(TraceKeys.class)
public class TraceWebAutoConfiguration {

	/**
	 * Pattern for URLs that should be skipped in tracing
	 */
	@Value("${spring.sleuth.web.skipPattern:}")
	private String skipPattern;

	@Bean
	public TraceWebAspect traceWebAspect(Tracer tracer, SpanNamer spanNamer) {
		return new TraceWebAspect(tracer, spanNamer);
	}

	@Bean
	@ConditionalOnMissingBean
	public TraceFilter traceFilter(Tracer tracer, TraceKeys traceKeys,
			SkipPatternProvider skipPatternProvider, SpanReporter spanReporter,
			SpanExtractor<HttpServletRequest> spanExtractor,
			SpanInjector<HttpServletResponse> spanInjector) {
		return new TraceFilter(tracer, traceKeys, skipPatternProvider.skipPattern(),
				spanReporter, spanExtractor, spanInjector);
	}

	@Bean
	public SpanExtractor<HttpServletRequest> httpServletRequestSpanExtractor(Random random,
			SkipPatternProvider skipPatternProvider) {
		return new HttpServletRequestExtractor(random, skipPatternProvider.skipPattern());
	}

	@Bean
	public SpanInjector<HttpServletResponse> httpServletResponseSpanInjector() {
		return new HttpServletResponseInjector();
	}

	@Configuration
	@ConditionalOnClass(ManagementServerProperties.class)
	@ConditionalOnMissingBean(SkipPatternProvider.class)
	protected static class SkipPatternProviderConfig {

		/**
		 * Pattern for URLs that should be skipped in tracing
		 */
		@Value("${spring.sleuth.web.skipPattern:}")
		private String skipPattern;

		@Bean
		@ConditionalOnBean(ManagementServerProperties.class)
		public SkipPatternProvider skipPatternForManagementServerProperties(
				final ManagementServerProperties managementServerProperties) {
			return new SkipPatternProvider() {
				@Override
				public Pattern skipPattern() {
					return getPatternForManagementServerProperties(
							managementServerProperties, SkipPatternProviderConfig.this.skipPattern);
				}
			};
		}

		/**
		 * Sets or appends {@link ManagementServerProperties#getContextPath()} to the
		 * skip pattern. If neither is available then sets the default one
		 */
		static Pattern getPatternForManagementServerProperties(
				ManagementServerProperties managementServerProperties, String skipPattern) {
			if (StringUtils.hasText(skipPattern) &&
					StringUtils.hasText(managementServerProperties.getContextPath())) {
				return Pattern.compile(skipPattern + "|" +
						managementServerProperties.getContextPath() + ".*");
			} else if (StringUtils.hasText(managementServerProperties.getContextPath())) {
				return Pattern.compile(managementServerProperties.getContextPath() + ".*");
			}
			return defaultSkipPattern(skipPattern);
		}

		@Bean
		@ConditionalOnMissingBean(ManagementServerProperties.class)
		public SkipPatternProvider defaultSkipPatternBeanIfManagementServerPropsArePresent() {
			return defaultSkipPatternProvider(this.skipPattern);
		}
	}

	@Bean
	@ConditionalOnMissingClass("org.springframework.boot.actuate.autoconfigure.ManagementServerProperties")
	@ConditionalOnMissingBean(SkipPatternProvider.class)
	public SkipPatternProvider defaultSkipPatternBean() {
		return defaultSkipPatternProvider(this.skipPattern);
	}

	private static SkipPatternProvider defaultSkipPatternProvider(final String skipPattern) {
		return new SkipPatternProvider() {
			@Override
			public Pattern skipPattern() {
				return defaultSkipPattern(skipPattern);
			}
		};
	}

	private static Pattern defaultSkipPattern(String skipPattern) {
		return StringUtils.hasText(skipPattern) ?
				Pattern.compile(skipPattern)
				: Pattern.compile(TraceFilter.DEFAULT_SKIP_PATTERN);
	}

	interface SkipPatternProvider {
		Pattern skipPattern();
	}
}
