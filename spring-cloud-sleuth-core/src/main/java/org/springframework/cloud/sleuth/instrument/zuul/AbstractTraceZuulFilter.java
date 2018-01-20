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

import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

/**
 * The pre and post filters use the same handler logic
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
abstract class AbstractTraceZuulFilter extends ZuulFilter {

	static final String ZUUL_CURRENT_SPAN =
			AbstractTraceZuulFilter.class.getName() + ".CURRENT_SPAN";

	static final Propagation.Setter<RequestContext, String> SETTER = new Propagation.Setter<RequestContext, String>() {
		@Override public void put(RequestContext carrier, String key, String value) {
			carrier.getZuulRequestHeaders().put(key, value);
		}

		@Override public String toString() {
			return "RequestContext::getZuulRequestHeaders::put";
		}
	};

	final Tracer tracer;
	HttpClientHandler<RequestContext, HttpServletResponse> handler;
	TraceContext.Injector<RequestContext> injector;

	AbstractTraceZuulFilter(HttpTracing httpTracing) {
		this.tracer = httpTracing.tracing().tracer();
		this.handler = HttpClientHandler
				.create(httpTracing, new AbstractTraceZuulFilter.HttpAdapter());
		this.injector = httpTracing.tracing().propagation().injector(SETTER);
	}

	static final class HttpAdapter
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
}