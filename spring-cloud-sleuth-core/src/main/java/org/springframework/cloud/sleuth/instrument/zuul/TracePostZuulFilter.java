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

package org.springframework.cloud.sleuth.instrument.zuul;

import javax.servlet.http.HttpServletResponse;

import brave.Tracer;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.servlet.HttpServletAdapter;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;

/**
 * A post request {@link ZuulFilter}
 *
 * @author Dave Syer
 * @since 1.0.0
 */
class TracePostZuulFilter extends ZuulFilter {

	private static final Log log = LogFactory.getLog(TracePostZuulFilter.class);

	private final HttpServerHandler handler;
	private final Tracer tracer;

	TracePostZuulFilter(HttpTracing httpTracing) {
		this.handler = HttpServerHandler.create(httpTracing,
				new HttpServletAdapter());
		this.tracer = httpTracing.tracing().tracer();
	}

	@Override
	public boolean shouldFilter() {
		return !httpStatusSuccessful(RequestContext.getCurrentContext().getResponse());
	}

	private boolean httpStatusSuccessful(HttpServletResponse response) {
		if (response.getStatus() == 0) {
			return false;
		}
		HttpStatus.Series httpStatusSeries = HttpStatus.Series.valueOf(response.getStatus());
		return httpStatusSeries == HttpStatus.Series.SUCCESSFUL || httpStatusSeries == HttpStatus.Series.REDIRECTION;
	}

	@Override
	public Object run() {
		if (log.isDebugEnabled()) {
			log.debug("Marking current span as handled");
		}
		HttpServletResponse response = RequestContext.getCurrentContext().getResponse();
		Throwable exception = RequestContext.getCurrentContext().getThrowable();
		this.handler.handleSend(response, exception, this.tracer.currentSpan());
		return null;
	}

	@Override
	public String filterType() {
		return "post";
	}

	@Override
	public int filterOrder() {
		return 0;
	}

}
