/*
 * Copyright 2015 the original author or authors.
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

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

/**8
 * A post request {@link ZuulFilter} that publishes an event upon start of the filtering
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class TracePostZuulFilter extends AbstractTraceZuulFilter {

	private static final Log log = LogFactory.getLog(TracePostZuulFilter.class);

	public static ZuulFilter create(Tracing tracing) {
		return new TracePostZuulFilter(HttpTracing.create(tracing));
	}

	public static ZuulFilter create(HttpTracing httpTracing) {
		return new TracePostZuulFilter(httpTracing);
	}

	TracePostZuulFilter(HttpTracing httpTracing) {
		super(httpTracing);
	}

	@Override
	public boolean shouldFilter() {
		return getCurrentSpan() != null;
	}

	@Override
	public Object run() {
		Span span = getCurrentSpan();
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			if (log.isDebugEnabled()) {
				log.debug("Closing current client span " + span);
			}
			HttpServletResponse response = RequestContext.getCurrentContext()
					.getResponse();
			this.handler.handleReceive(response, null, span);
		} finally {
			if (span != null) {
				span.finish();
			}
		}
		return null;
	}

	private Span getCurrentSpan() {
		RequestContext ctx = RequestContext.getCurrentContext();
		if (ctx == null || ctx.getRequest() == null) {
			return null;
		}
		return (Span) ctx.getRequest().getAttribute(ZUUL_CURRENT_SPAN);
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