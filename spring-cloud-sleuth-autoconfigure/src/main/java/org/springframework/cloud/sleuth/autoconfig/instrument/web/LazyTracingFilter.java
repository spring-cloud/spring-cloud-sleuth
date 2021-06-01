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

package org.springframework.cloud.sleuth.autoconfig.instrument.web;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.instrument.web.servlet.TracingFilter;

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
			this.tracingFilter = TracingFilter.create(this.beanFactory.getBean(CurrentTraceContext.class),
					this.beanFactory.getBean(HttpServerHandler.class));
		}
		return this.tracingFilter;
	}

}
