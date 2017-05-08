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

import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.actuate.autoconfigure.ManagementServerProperties;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import static javax.servlet.DispatcherType.ASYNC;
import static javax.servlet.DispatcherType.ERROR;
import static javax.servlet.DispatcherType.FORWARD;
import static javax.servlet.DispatcherType.INCLUDE;
import static javax.servlet.DispatcherType.REQUEST;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables tracing to HTTP requests for Web based applications.
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
@AutoConfigureAfter(TraceHttpAutoConfiguration.class)
@EnableConfigurationProperties({ SleuthWebProperties.class })
public class TraceWebAutoConfiguration {

	/**
	 * Nested config that configures Web MVC if it's present
	 * (without adding a runtime dependency to it)
	 */
	@Configuration
	@ConditionalOnClass(WebMvcConfigurerAdapter.class)
	@Import(TraceWebMvcConfigurer.class)
	protected static class TraceWebMvcAutoConfiguration {
	}

	@Bean
	public TraceWebAspect traceWebAspect(Tracer tracer, SpanNamer spanNamer) {
		return new TraceWebAspect(tracer, spanNamer);
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.data.rest.webmvc.support.DelegatingHandlerMapping")
	public TraceSpringDataBeanPostProcessor traceSpringDataBeanPostProcessor(BeanFactory beanFactory) {
		return new TraceSpringDataBeanPostProcessor(beanFactory);
	}

	@Bean
	public FilterRegistrationBean traceWebFilter(Tracer tracer, TraceKeys traceKeys,
			SkipPatternProvider skipPatternProvider, SpanReporter spanReporter,
			SpanExtractor<HttpServletRequest> spanExtractor,
			HttpTraceKeysInjector httpTraceKeysInjector, TraceFilter traceFilter) {
		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(traceFilter);
		filterRegistrationBean.setDispatcherTypes(ASYNC, ERROR, FORWARD, INCLUDE, REQUEST);
		filterRegistrationBean.setOrder(TraceFilter.ORDER);
		return filterRegistrationBean;
	}

	@Bean
	public TraceFilter traceFilter(Tracer tracer, TraceKeys traceKeys,
			SkipPatternProvider skipPatternProvider, SpanReporter spanReporter,
			SpanExtractor<HttpServletRequest> spanExtractor,
			HttpTraceKeysInjector httpTraceKeysInjector) {
		return new TraceFilter(tracer, traceKeys, skipPatternProvider.skipPattern(),
				spanReporter, spanExtractor, httpTraceKeysInjector);
	}

	@Bean
	public SpanExtractor<HttpServletRequest> httpServletRequestSpanExtractor(
			SkipPatternProvider skipPatternProvider) {
		return new HttpServletRequestExtractor(skipPatternProvider.skipPattern());
	}

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
							managementServerProperties, sleuthWebProperties);
				}
			};
		}

		/**
		 * Sets or appends {@link ManagementServerProperties#getContextPath()} to the
		 * skip pattern. If neither is available then sets the default one
		 */
		static Pattern getPatternForManagementServerProperties(
				ManagementServerProperties managementServerProperties,
				SleuthWebProperties sleuthWebProperties) {
			String skipPattern = sleuthWebProperties.getSkipPattern();
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
		public SkipPatternProvider defaultSkipPatternBeanIfManagementServerPropsArePresent(SleuthWebProperties sleuthWebProperties) {
			return defaultSkipPatternProvider(sleuthWebProperties.getSkipPattern());
		}
	}

	@Bean
	@ConditionalOnMissingClass("org.springframework.boot.actuate.autoconfigure.ManagementServerProperties")
	@ConditionalOnMissingBean(SkipPatternProvider.class)
	public SkipPatternProvider defaultSkipPatternBean(SleuthWebProperties sleuthWebProperties) {
		return defaultSkipPatternProvider(sleuthWebProperties.getSkipPattern());
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
				: Pattern.compile(SleuthWebProperties.DEFAULT_SKIP_PATTERN);
	}

	interface SkipPatternProvider {
		Pattern skipPattern();
	}
}
