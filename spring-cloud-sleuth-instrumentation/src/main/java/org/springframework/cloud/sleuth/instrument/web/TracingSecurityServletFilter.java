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

package org.springframework.cloud.sleuth.instrument.web;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

/**
 * A filter that adds security related tags.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
// TODO: Check the DelegatingFilterProxy
public class TracingSecurityServletFilter extends GenericFilterBean {

	private final Tracer tracer;

	public TracingSecurityServletFilter(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
			throws ServletException, IOException {

		// SecurityContextHolder.setContext();

		// Object contextCleared;
		// tracer.currentSpan().event("contexSet");

		// actual clearing happens

		SecurityContext securityContext = getContext();
		if (securityContext != null) {
			Span span = this.tracer.currentSpan();
			if (span != null) {
				Span nextSpan = this.tracer.nextSpan().start();
				TracingSecurityTagSetter.setSecurityTags(nextSpan, securityContext.getAuthentication());
				nextSpan.end();


			}
		}
		filterChain.doFilter(servletRequest, servletResponse);
	}

	SecurityContext getContext() {
		return SecurityContextHolder.getContext();
	}

	/**
	 * Lazy version of the {@link TracingSecurityServletFilter}.
	 * @param beanFactory bean factory
	 * @return lazy version of the filter
	 */
	public static Filter lazy(BeanFactory beanFactory) {
		return new Filter() {

			private TracingSecurityServletFilter tracingSecurityServletFilter;

			@Override
			public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
					FilterChain filterChain) throws IOException, ServletException {
				tracingSecurityFilter().doFilter(servletRequest, servletResponse, filterChain);
			}

			private TracingSecurityServletFilter tracingSecurityFilter() {
				if (this.tracingSecurityServletFilter == null) {
					this.tracingSecurityServletFilter = new TracingSecurityServletFilter(
							beanFactory.getBean(Tracer.class));
				}
				return this.tracingSecurityServletFilter;
			}
		};
	}

}
