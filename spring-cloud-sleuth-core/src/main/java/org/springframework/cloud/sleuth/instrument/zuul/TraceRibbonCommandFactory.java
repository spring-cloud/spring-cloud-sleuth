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

import brave.Span;
import brave.http.HttpTracing;
import org.springframework.cloud.netflix.ribbon.support.RibbonCommandContext;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommand;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;

/**
 * Propagates traces downstream via http headers that contain trace metadata.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
class TraceRibbonCommandFactory implements RibbonCommandFactory {

	private final RibbonCommandFactory delegate;
	private final HttpTracing tracing;

	public TraceRibbonCommandFactory(RibbonCommandFactory delegate,
			HttpTracing tracing) {
		this.delegate = delegate;
		this.tracing = tracing;
	}

	@Override
	public RibbonCommand create(RibbonCommandContext context) {
		RibbonCommand ribbonCommand = this.delegate.create(context);
		Span span = this.tracing.tracing().tracer().currentSpan();
		this.tracing.clientParser().request(new TraceRibbonCommandFactory.HttpAdapter(), context, span);
		return ribbonCommand;
	}

	static final class HttpAdapter
			extends brave.http.HttpClientAdapter<RibbonCommandContext, RibbonCommand> {

		@Override public String method(RibbonCommandContext request) {
			return request.getMethod();
		}

		@Override public String url(RibbonCommandContext request) {
			return request.getUri();
		}

		@Override public String requestHeader(RibbonCommandContext request, String name) {
			Object result = request.getHeaders().getFirst(name);
			return result != null ? result.toString() : null;
		}

		@Override public Integer statusCode(RibbonCommand response) {
			throw new UnsupportedOperationException("RibbonCommand doesn't support status code");
		}
	}
}
