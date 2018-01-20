package org.springframework.cloud.sleuth.instrument.zuul;

import javax.servlet.http.HttpServletResponse;

import com.netflix.zuul.context.RequestContext;

final class HttpAdapter
		extends brave.http.HttpClientAdapter<RequestContext, HttpServletResponse> {

	@Override public String method(RequestContext request) {
		return request.getRequest().getMethod();
	}

	@Override public String url(RequestContext request) {
		return request.getRequest().getRequestURI();
	}

	@Override public String requestHeader(RequestContext request, String name) {
		Object result = request.getZuulRequestHeaders().get(name);
		return result != null ? result.toString() : null;
	}

	@Override public Integer statusCode(HttpServletResponse response) {
		return response.getStatus();
	}
}