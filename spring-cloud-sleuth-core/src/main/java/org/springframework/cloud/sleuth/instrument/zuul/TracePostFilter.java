package org.springframework.cloud.sleuth.instrument.zuul;

import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

/**
 * @author Spencer Gibb
 */
public class TracePostFilter extends ZuulFilter {

	private Trace trace;

	public TracePostFilter(Trace trace) {
		this.trace = trace;
	}

	@Override
	public String filterType() {
		return "post";
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
		TraceScope traceScope = (TraceScope) RequestContext.getCurrentContext().get(
				"traceScope");

		if (traceScope != null) {
			traceScope.close();
		}

		return null;
	}
}
