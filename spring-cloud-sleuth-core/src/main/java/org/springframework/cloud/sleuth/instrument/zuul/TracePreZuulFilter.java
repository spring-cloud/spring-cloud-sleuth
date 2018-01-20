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

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.instrument.web.TraceFilter;
import org.springframework.cloud.sleuth.instrument.web.TraceRequestAttributes;

import com.netflix.zuul.ExecutionStatus;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.ZuulFilterResult;
import com.netflix.zuul.context.RequestContext;

/**
 * A pre request {@link ZuulFilter} that sets tracing related headers on the request
 * from the current span. We're doing so to ensure tracing propagates to the next hop.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class TracePreZuulFilter extends AbstractTraceZuulFilter {

	private static final Log log = LogFactory.getLog(TracePreZuulFilter.class);
	private static final String TRACE_REQUEST_ATTR = TraceFilter.class.getName() + ".TRACE";
	private static final String TRACE_CLOSE_SPAN_REQUEST_ATTR =
			TraceFilter.class.getName() + ".CLOSE_SPAN";

	public static ZuulFilter create(Tracing tracing, ErrorParser errorParser) {
		return new TracePreZuulFilter(HttpTracing.create(tracing), errorParser);
	}

	public static ZuulFilter create(HttpTracing httpTracing, ErrorParser errorParser) {
		return new TracePreZuulFilter(httpTracing, errorParser);
	}

	private final ErrorParser errorParser;

	TracePreZuulFilter(HttpTracing httpTracing, ErrorParser errorParser) {
		super(httpTracing);
		this.errorParser = errorParser;
	}

	@Override public ZuulFilterResult runFilter() {
		RequestContext ctx = RequestContext.getCurrentContext();
		Span span = this.handler.handleSend(this.injector, ctx);
		ZuulFilterResult result = null;
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			markRequestAsHandled(ctx, span);
			if (log.isDebugEnabled()) {
				log.debug("New Zuul Span is " + span + "");
			}
			result = super.runFilter();
			return result;
		}
		finally {
			if (result != null && ExecutionStatus.SUCCESS != result.getStatus()) {
				if (log.isDebugEnabled()) {
					log.debug(
							"The result of Zuul filter execution was not successful thus "
									+ "will close the current span " + span);
				}
				this.errorParser.parseErrorTags(span, result.getException());
				span.finish();
			}
		}
	}

	// TraceFilter will not create the "fallback" span
	private void markRequestAsHandled(RequestContext ctx, Span span) {
		ctx.getRequest()
				.setAttribute(TraceRequestAttributes.HANDLED_SPAN_REQUEST_ATTR, "true");
		ctx.getRequest().setAttribute(TraceRequestAttributes.ERROR_HANDLED_SPAN_REQUEST_ATTR,
				"true");
		ctx.getRequest().setAttribute(TRACE_REQUEST_ATTR, span);
		ctx.getRequest().setAttribute(TRACE_CLOSE_SPAN_REQUEST_ATTR, true);
		ctx.getRequest().setAttribute(ZUUL_CURRENT_SPAN, span);
	}

	@Override public String filterType() {
		return "pre";
	}

	@Override public int filterOrder() {
		return 0;
	}

	@Override public boolean shouldFilter() {
		return true;
	}

	@Override public Object run() {
		return null;
	}
}