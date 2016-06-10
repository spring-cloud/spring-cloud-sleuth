/*
 * Copyright 2013-2015 the original author or authors.
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

import java.io.InputStream;
import java.net.URISyntaxException;

import com.netflix.client.http.HttpRequest;
import com.netflix.niws.client.http.RestClient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.netflix.zuul.filters.route.RestClientRibbonCommand;
import org.springframework.cloud.netflix.zuul.filters.route.RestClientRibbonCommandFactory;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.util.MultiValueMap;

/**
 * Propagates traces downstream via http headers that contain trace metadata.
 *
 * @author Spencer Gibb
 * @since 1.0.0
 */
public class TraceRestClientRibbonCommandFactory extends RestClientRibbonCommandFactory {

	private static final Log log = LogFactory.getLog(TraceRestClientRibbonCommandFactory.class);

	private final Tracer tracer;
	private final SpanInjector<HttpRequest.Builder> spanInjector;
	private final HttpTraceKeysInjector httpTraceKeysInjector;

	public TraceRestClientRibbonCommandFactory(SpringClientFactory clientFactory,
			Tracer tracer, SpanInjector<HttpRequest.Builder> spanInjector,
			HttpTraceKeysInjector httpTraceKeysInjector) {
		super(clientFactory);
		this.tracer = tracer;
		this.spanInjector = spanInjector;
		this.httpTraceKeysInjector = httpTraceKeysInjector;
	}

	@Override
	@SuppressWarnings("deprecation")
	public RestClientRibbonCommand create(RibbonCommandContext context) {
		RestClient restClient = getClientFactory().getClient(context.getServiceId(),
				RestClient.class);
		try {
			return new TraceRestClientRibbonCommand(context.getServiceId(), restClient,
					getVerb(context.getVerb()), context.getUri(), context.getRetryable(),
					context.getHeaders(), context.getParams(), context.getRequestEntity(),
					this.tracer, this.spanInjector, this.httpTraceKeysInjector);
		}
		catch (URISyntaxException e) {
			log.error("Exception occurred while trying to create the TraceRestClientRibbonCommand", e);
			throw new RuntimeException(e);
		}
	}

	class TraceRestClientRibbonCommand extends RestClientRibbonCommand {

		private final Tracer tracer;
		private final SpanInjector<HttpRequest.Builder> spanInjector;
		private final HttpTraceKeysInjector httpTraceKeysInjector;

		@SuppressWarnings("deprecation")
		public TraceRestClientRibbonCommand(String commandKey, RestClient restClient,
				HttpRequest.Verb verb, String uri, Boolean retryable,
				MultiValueMap<String, String> headers,
				MultiValueMap<String, String> params, InputStream requestEntity,
				Tracer tracer, SpanInjector<HttpRequest.Builder> spanInjector,
				HttpTraceKeysInjector httpTraceKeysInjector)
						throws URISyntaxException {
			super(commandKey, restClient, verb, uri, retryable, headers, params,
					requestEntity);
			this.tracer = tracer;
			this.spanInjector = spanInjector;
			this.httpTraceKeysInjector = httpTraceKeysInjector;
		}

		@Override
		protected void customizeRequest(HttpRequest.Builder requestBuilder) {
			Span span = getCurrentSpan();
			this.spanInjector.inject(span, requestBuilder);
			this.httpTraceKeysInjector.addRequestTags(span, getUri(), getVerb().verb());
			span.logEvent(Span.CLIENT_SEND);
			if (log.isDebugEnabled()) {
				log.debug("Span in RibbonCommandFactory is" + span);
			}
		}

		private Span getCurrentSpan() {
			return this.tracer.getCurrentSpan();
		}

	}
}
