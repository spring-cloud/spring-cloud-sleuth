package org.springframework.cloud.sleuth.instrument.web;

import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Spencer Gibb
 */
public class TraceHandlerInterceptor implements HandlerInterceptor {

	private static final String ATTR_NAME = "__CURRENT_TRACE_HANDLER_TRACE_SCOPE_ATTR___";

	private final Trace trace;

	public TraceHandlerInterceptor(Trace trace) {
		this.trace = trace;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		//TODO: get trace data from request?
		//TODO: what is the description?
		TraceScope scope = trace.startSpan("traceHandlerInterceptor");
		request.setAttribute(ATTR_NAME, scope);
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
		TraceScope scope = TraceScope.class.cast(request.getAttribute(ATTR_NAME));
		if (scope != null) {
			scope.close();
		}
	}
}
