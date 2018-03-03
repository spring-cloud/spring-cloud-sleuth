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

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Filter running after {@link brave.servlet.TracingFilter}
 * that logs uncaught exceptions
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
class ExceptionLoggingFilter implements Filter {

	private static final Log log = LogFactory.getLog(ExceptionLoggingFilter.class);

	@Override public void init(FilterConfig filterConfig) throws ServletException {

	}

	@Override public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		try {
			chain.doFilter(request, response);
		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("Uncaught exception thrown", e);
			}
			throw e;
		}
	}

	@Override public void destroy() {

	}
}
