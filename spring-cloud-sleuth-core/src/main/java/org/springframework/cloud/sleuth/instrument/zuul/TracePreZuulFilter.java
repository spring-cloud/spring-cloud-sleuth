/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.zuul;

import java.lang.invoke.MethodHandles;
import java.net.URI;

import com.netflix.zuul.ExecutionStatus;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.ZuulFilterResult;
import com.netflix.zuul.context.RequestContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpSpanInjector;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.instrument.web.TraceFilter;
import org.springframework.cloud.sleuth.instrument.web.TraceRequestAttributes;

/**
 * A pre request {@link ZuulFilter} that sets tracing related headers on the request
 * from the current span. We're doing so to ensure tracing propagates to the next hop.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class TracePreZuulFilter extends ZuulFilter {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private static final String TRACE_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".TRACE";
	private static final String TRACE_CLOSE_SPAN_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".CLOSE_SPAN";

	private static final String ZUUL_COMPONENT = "zuul";

	private final Tracer tracer;
	private final HttpSpanInjector spanInjector;
	private final HttpTraceKeysInjector httpTraceKeysInjector;
	private final ErrorParser errorParser;

	public TracePreZuulFilter(Tracer tracer, HttpSpanInjector spanInjector,
			HttpTraceKeysInjector httpTraceKeysInjector, ErrorParser errorParser) {
		this.tracer = tracer;
		this.spanInjector = spanInjector;
		this.httpTraceKeysInjector = httpTraceKeysInjector;
		this.errorParser = errorParser;
	}

	@Override
	public boolean shouldFilter() {
		return true;
	}

	@Override
	public Object run() {
		return null;
	}

	@Override
	public ZuulFilterResult runFilter() {
		RequestContext ctx = RequestContext.getCurrentContext();
		Span span = getCurrentSpan();
		if (log.isDebugEnabled()) {
			log.debug("Current span is " + span + "");
		}
		markRequestAsHandled(ctx);
		Span newSpan = this.tracer.createSpan(span.getName(), span);
		try {
			newSpan.tag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, ZUUL_COMPONENT);
			this.spanInjector.inject(newSpan, new RequestContextTextMap(ctx));
			this.httpTraceKeysInjector.addRequestTags(newSpan, URI.create(ctx.getRequest().getRequestURI()), ctx.getRequest().getMethod());
			if (log.isDebugEnabled()) {
				log.debug("New Zuul Span is " + newSpan + "");
			}
			if (log.isDebugEnabled()) {
				log.debug("Setting attributes for TraceFilter to pick up later");
			}
			RequestContext.getCurrentContext().getRequest().setAttribute(TRACE_REQUEST_ATTR, this.tracer.getCurrentSpan());
			RequestContext.getCurrentContext().getRequest().setAttribute(TRACE_CLOSE_SPAN_REQUEST_ATTR, true);
			ZuulFilterResult result = super.runFilter();
			if (log.isDebugEnabled()) {
				log.debug("Result of Zuul filter is [" + result.getStatus() + "]");
			}
			if (ExecutionStatus.SUCCESS != result.getStatus()) {
				if (log.isDebugEnabled()) {
					log.debug("The result of Zuul filter execution was not successful thus "
							+ "will close the current span " + newSpan);
				}
				this.errorParser.parseErrorTags(newSpan, result.getException());
				this.tracer.close(newSpan);
			}
			return result;
		} catch (Exception e) {
			this.tracer.close(newSpan);
			throw e;
		}
	}

	// TraceFilter will not create the "fallback" span
	private void markRequestAsHandled(RequestContext ctx) {
		ctx.getRequest().setAttribute(TraceRequestAttributes.HANDLED_SPAN_REQUEST_ATTR, "true");
		ctx.getRequest().setAttribute(TraceRequestAttributes.ERROR_HANDLED_SPAN_REQUEST_ATTR, "true");
	}

	private Span getCurrentSpan() {
		return this.tracer.getCurrentSpan();
	}


	@Override
	public String filterType() {
		return "pre";
	}

	@Override
	public int filterOrder() {
		return 0;
	}

}
