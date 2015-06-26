package org.springframework.cloud.sleuth.instrument.zuul;

import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;
import static org.springframework.util.StringUtils.hasText;

import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

/**
 * @author Spencer Gibb
 */
public class TracePreFilter extends ZuulFilter {

	private Trace trace;

	public TracePreFilter(Trace trace) {
		this.trace = trace;
	}

	@Override
	public String filterType() {
		return "pre";
	}

	@Override
	public int filterOrder() {
		return 0;
	}

	@Override
	public boolean shouldFilter() {
		return true;
	}

	@Override
	public Object run() {
		RequestContext context = RequestContext.getCurrentContext();
		HttpServletRequest request = context.getRequest();

		String spanId = request.getHeader(SPAN_ID_NAME);
		String traceId = request.getHeader(TRACE_ID_NAME);
		TraceScope traceScope = null;
		if (hasText(spanId) && hasText(traceId)) {

			Span span = MilliSpan.builder().traceId(traceId)
					.parents(Collections.singletonList(spanId))
					// TODO: use parent() when lombok plugin supports it
					.build();

			// TODO: trace description?
			traceScope = trace.startSpan("traceZuulFilter", span);
		}
		else {
			traceScope = trace.startSpan("traceZuulFilter");

		}

		context.set("traceScope", traceScope);

		return null;
	}
}
