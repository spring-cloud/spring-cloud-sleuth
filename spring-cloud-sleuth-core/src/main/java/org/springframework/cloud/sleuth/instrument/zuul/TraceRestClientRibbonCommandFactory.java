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

import com.netflix.client.http.HttpRequest;
import com.netflix.niws.client.http.RestClient;
import lombok.SneakyThrows;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.netflix.zuul.filters.route.RestClientRibbonCommand;
import org.springframework.cloud.netflix.zuul.filters.route.RestClientRibbonCommandFactory;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceAccessor;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.MultiValueMap;

import java.io.InputStream;
import java.net.URISyntaxException;

/**
 * @author Spencer Gibb
 */
public class TraceRestClientRibbonCommandFactory extends RestClientRibbonCommandFactory
		implements ApplicationEventPublisherAware {

	private ApplicationEventPublisher publisher;

	private final TraceAccessor accessor;

	public TraceRestClientRibbonCommandFactory(SpringClientFactory clientFactory,
			TraceAccessor accessor) {
		super(clientFactory);
		this.accessor = accessor;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	@SneakyThrows
	@SuppressWarnings("deprecation")
	public RestClientRibbonCommand create(RibbonCommandContext context) {
		RestClient restClient = getClientFactory().getClient(context.getServiceId(),
				RestClient.class);
		return new TraceRestClientRibbonCommand(context.getServiceId(), restClient,
				getVerb(context.getVerb()), context.getUri(), context.getRetryable(),
				context.getHeaders(), context.getParams(), context.getRequestEntity(),
				this.publisher, this.accessor);
	}

	class TraceRestClientRibbonCommand extends RestClientRibbonCommand {

		private ApplicationEventPublisher publisher;

		private final TraceAccessor accessor;

		@SuppressWarnings("deprecation")
		public TraceRestClientRibbonCommand(String commandKey, RestClient restClient,
				HttpRequest.Verb verb, String uri, Boolean retryable,
				MultiValueMap<String, String> headers,
				MultiValueMap<String, String> params, InputStream requestEntity,
				ApplicationEventPublisher publisher, TraceAccessor accessor)
						throws URISyntaxException {
			super(commandKey, restClient, verb, uri, retryable, headers, params,
					requestEntity);
			this.publisher = publisher;
			this.accessor = accessor;
		}

		@Override
		protected void customizeRequest(HttpRequest.Builder requestBuilder) {
			Span span = getCurrentSpan();
			if (span == null) {
				setHeader(requestBuilder, Span.NOT_SAMPLED_NAME, "");
				return;
			}
			setHeader(requestBuilder, Span.TRACE_ID_NAME, span.getTraceId());
			setHeader(requestBuilder, Span.SPAN_ID_NAME, span.getSpanId());
			setHeader(requestBuilder, Span.SPAN_NAME_NAME, span.getName());
			setHeader(requestBuilder, Span.PARENT_ID_NAME,
					getParentId(span));
			setHeader(requestBuilder, Span.PROCESS_ID_NAME,
					span.getProcessId());
			publish(new ClientSentEvent(this, span));
		}

		private void publish(ApplicationEvent event) {
			if (this.publisher != null) {
				this.publisher.publishEvent(event);
			}
		}

		private Long getParentId(Span span) {
			return !span.getParents().isEmpty()
					? span.getParents().get(0) : null;
		}

		public void setHeader(HttpRequest.Builder builder, String name, String value) {
			if (value != null && this.accessor.isTracing()) {
				builder.header(name, value);
			}
		}

		public void setHeader(HttpRequest.Builder builder, String name, Long value) {
			setHeader(builder, name, Span.IdConverter.toHex(value));
		}

		private Span getCurrentSpan() {
			return this.accessor.getCurrentSpan();
		}

	}
}
