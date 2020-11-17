/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.instrument.web.mvc.SpanCustomizingAsyncHandlerInterceptor;
import org.springframework.cloud.sleuth.instrument.web.servlet.TracingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables tracing to HTTP requests.
 *
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@SleuthWebEnabled
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(SpanCustomizingAsyncHandlerInterceptor.class)
public class TraceWebServletConfiguration {

	/**
	 * Default filter order for the Http tracing filter.
	 */
	public static final int TRACING_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(value = "spring.sleuth.web.servlet.enabled", matchIfMissing = true)
	static class ServletConfiguration {

		@Bean
		TraceWebAspect traceWebAspect(Tracer tracer, CurrentTraceContext currentTraceContext, SpanNamer spanNamer) {
			return new TraceWebAspect(tracer, currentTraceContext, spanNamer);
		}

		@Bean
		FilterRegistrationBean traceWebFilter(BeanFactory beanFactory, SleuthWebProperties webProperties) {
			FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(
					new LazyTracingFilter(beanFactory));
			filterRegistrationBean.setDispatcherTypes(DispatcherType.ASYNC, DispatcherType.ERROR,
					DispatcherType.FORWARD, DispatcherType.INCLUDE, DispatcherType.REQUEST);
			filterRegistrationBean.setOrder(webProperties.getFilterOrder());
			return filterRegistrationBean;
		}

		@Bean
		@ConditionalOnMissingBean
		TracingFilter tracingFilter(CurrentTraceContext currentTraceContext, HttpServerHandler httpServerHandler) {
			return TracingFilter.create(currentTraceContext, httpServerHandler);
		}

		/**
		 * Nested config that configures Web MVC if it's present (without adding a runtime
		 * dependency to it).
		 */
		@Configuration(proxyBeanMethods = false)
		@ConditionalOnClass(WebMvcConfigurer.class)
		@Import(TraceWebMvcConfigurer.class)
		protected static class TraceWebMvcAutoConfiguration {

		}

	}

}

final class LazyTracingFilter implements Filter {

	private final BeanFactory beanFactory;

	private Filter tracingFilter;

	LazyTracingFilter(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		tracingFilter().init(filterConfig);
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		tracingFilter().doFilter(request, response, chain);
	}

	@Override
	public void destroy() {
		tracingFilter().destroy();
	}

	private Filter tracingFilter() {
		if (this.tracingFilter == null) {
			this.tracingFilter = this.beanFactory.getBean(TracingFilter.class);
		}
		return this.tracingFilter;
	}

}
