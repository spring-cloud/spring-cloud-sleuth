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

package org.springframework.cloud.sleuth.zipkin.web;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.github.kristofa.brave.EndPointSubmitter;

/**
 * @author Spencer Gibb
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class ZipkinFilter implements Filter {

	@Value("${spring.application.name:application}")
	private String serviceName;

	private EndPointSubmitter endPointSubmitter;

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public ZipkinFilter(EndPointSubmitter endPointSubmitter) {
		this.endPointSubmitter = endPointSubmitter;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// NOOP
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		if (!this.endPointSubmitter.endPointSubmitted()) {
			final String localAddr = request.getLocalAddr();
			final int localPort = request.getLocalPort();
			final String contextPath = this.serviceName
					+ ((request instanceof HttpServletRequest) ? ((HttpServletRequest) request)
							.getContextPath() : "");
			this.endPointSubmitter.submit(localAddr, localPort, contextPath);
		}
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
		// NOOP
	}
}
