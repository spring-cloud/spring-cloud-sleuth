/*
 * Copyright 2013-2019 the original author or authors.
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

import javax.servlet.DispatcherType;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.servlet.TracingFilter;
import brave.spring.webmvc.SpanCustomizingAsyncHandlerInterceptor;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables tracing to HTTP requests.
 *
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.web.enabled", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(HttpTracing.class)
@AutoConfigureAfter(TraceHttpAutoConfiguration.class)
@Import(SpanCustomizingAsyncHandlerInterceptor.class)
public class TraceWebServletAutoConfiguration {

	/**
	 * Default filter order for the Http tracing filter.
	 */
	public static final int TRACING_FILTER_ORDER = TraceHttpAutoConfiguration.TRACING_FILTER_ORDER;

	@Bean
	@ConditionalOnClass(name = "org.springframework.data.rest.webmvc.support.DelegatingHandlerMapping")
	public static TraceSpringDataBeanPostProcessor traceSpringDataBeanPostProcessor(
			ApplicationContext applicationContext) {
		return new TraceSpringDataBeanPostProcessor(applicationContext);
	}

	@Bean
	TraceWebAspect traceWebAspect(Tracing tracing, SpanNamer spanNamer) {
		return new TraceWebAspect(tracing, spanNamer);
	}

	@Bean
	public FilterRegistrationBean traceWebFilter(TracingFilter tracingFilter,
			SleuthWebProperties webProperties) {
		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(
				tracingFilter);
		filterRegistrationBean.setDispatcherTypes(DispatcherType.ASYNC,
				DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.INCLUDE,
				DispatcherType.REQUEST);
		filterRegistrationBean.setOrder(webProperties.getFilterOrder());
		return filterRegistrationBean;
	}

	// TODO: Rename to exception-logging-filter for 3.0
	@Bean
	@ConditionalOnProperty(value = "spring.sleuth.web.exception-logging-filter-enabled", matchIfMissing = true)
	public FilterRegistrationBean exceptionThrowingFilter(
			SleuthWebProperties webProperties) {
		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(
				new ExceptionLoggingFilter());
		filterRegistrationBean.setDispatcherTypes(DispatcherType.ASYNC,
				DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.INCLUDE,
				DispatcherType.REQUEST);
		filterRegistrationBean.setOrder(webProperties.getFilterOrder());
		return filterRegistrationBean;
	}

	@Bean
	@ConditionalOnMissingBean
	public TracingFilter tracingFilter(HttpTracing tracing) {
		return (TracingFilter) TracingFilter.create(tracing);
	}

	/**
	 * Nested config that configures Web MVC if it's present (without adding a runtime
	 * dependency to it).
	 */
	@Configuration
	@ConditionalOnClass(WebMvcConfigurer.class)
	@Import(TraceWebMvcConfigurer.class)
	protected static class TraceWebMvcAutoConfiguration {

	}

}
