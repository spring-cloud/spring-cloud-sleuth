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
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static javax.servlet.DispatcherType.ASYNC;
import static javax.servlet.DispatcherType.ERROR;
import static javax.servlet.DispatcherType.FORWARD;
import static javax.servlet.DispatcherType.INCLUDE;
import static javax.servlet.DispatcherType.REQUEST;

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

	public static final int TRACING_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

	/**
	 * Nested config that configures Web MVC if it's present (without adding a runtime
	 * dependency to it)
	 */
	@Configuration
	@ConditionalOnClass(WebMvcConfigurer.class)
	@Import(TraceWebMvcConfigurer.class)
	protected static class TraceWebMvcAutoConfiguration {
	}

	@Bean
	TraceWebAspect traceWebAspect(Tracing tracing, SpanNamer spanNamer) {
		return new TraceWebAspect(tracing, spanNamer);
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.data.rest.webmvc.support.DelegatingHandlerMapping")
	public static TraceSpringDataBeanPostProcessor traceSpringDataBeanPostProcessor(
			ApplicationContext applicationContext) {
		return new TraceSpringDataBeanPostProcessor(applicationContext);
	}
	
	@Bean
	public FilterRegistrationBean traceWebFilter(
			TracingFilter tracingFilter) {
		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(tracingFilter);
		filterRegistrationBean.setDispatcherTypes(ASYNC, ERROR, FORWARD, INCLUDE, REQUEST);
		filterRegistrationBean.setOrder(TraceWebServletAutoConfiguration.TRACING_FILTER_ORDER);
		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean exceptionThrowingFilter() {
		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(new ExceptionLoggingFilter());
		filterRegistrationBean.setDispatcherTypes(ASYNC, ERROR, FORWARD, INCLUDE, REQUEST);
		filterRegistrationBean.setOrder(TraceWebServletAutoConfiguration.TRACING_FILTER_ORDER + 1);
		return filterRegistrationBean;
	}

	@Bean
	@ConditionalOnMissingBean
	public TracingFilter tracingFilter(HttpTracing tracing) {
		return (TracingFilter) TracingFilter.create(tracing);
	}
}
