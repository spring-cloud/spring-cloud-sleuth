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

import static org.springframework.cloud.sleuth.Trace.NOT_SAMPLED_NAME;
import static org.springframework.cloud.sleuth.Trace.PARENT_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.PROCESS_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.SPAN_NAME_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;
import static org.springframework.cloud.sleuth.TraceContextHolder.getCurrentSpan;
import static org.springframework.cloud.sleuth.TraceContextHolder.isTracing;

import java.io.InputStream;
import java.net.URISyntaxException;

import lombok.SneakyThrows;

import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.netflix.zuul.filters.route.RestClientRibbonCommand;
import org.springframework.cloud.netflix.zuul.filters.route.RestClientRibbonCommandFactory;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.MultiValueMap;

import com.netflix.client.http.HttpRequest;
import com.netflix.niws.client.http.RestClient;

/**
 * @author Spencer Gibb
 */
public class TraceRestClientRibbonCommandFactory extends RestClientRibbonCommandFactory
	implements ApplicationEventPublisherAware {

	private ApplicationEventPublisher publisher;

	public TraceRestClientRibbonCommandFactory(SpringClientFactory clientFactory) {
		super(clientFactory);
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
		return new TraceRestClientRibbonCommand(
				context.getServiceId(), restClient, getVerb(context.getVerb()),
				context.getUri(), context.getRetryable(), context.getHeaders(),
				context.getParams(), context.getRequestEntity(), publisher);
	}

	class TraceRestClientRibbonCommand extends RestClientRibbonCommand {

		private ApplicationEventPublisher publisher;

		@SuppressWarnings("deprecation")
		public TraceRestClientRibbonCommand(String commandKey, RestClient restClient, HttpRequest.Verb verb, String uri, Boolean retryable, MultiValueMap<String, String> headers, MultiValueMap<String, String> params, InputStream requestEntity, ApplicationEventPublisher publisher) throws URISyntaxException {
			super(commandKey, restClient, verb, uri, retryable, headers, params, requestEntity);
			this.publisher = publisher;
		}

		@Override
		protected void customizeRequest(HttpRequest.Builder requestBuilder) {
			if (getCurrentSpan() == null) {
				setHeader(requestBuilder, NOT_SAMPLED_NAME, "");
				return;
			}

			setHeader(requestBuilder, SPAN_ID_NAME, getCurrentSpan().getSpanId());
			setHeader(requestBuilder, TRACE_ID_NAME, getCurrentSpan().getTraceId());
			setHeader(requestBuilder, SPAN_NAME_NAME, getCurrentSpan().getName());
			setHeader(requestBuilder, PARENT_ID_NAME, getParentId(getCurrentSpan()));
			setHeader(requestBuilder, PROCESS_ID_NAME, getCurrentSpan().getProcessId());
			publish(new ClientSentEvent(this, getCurrentSpan()));
		}

		private void publish(ApplicationEvent event) {
			if (this.publisher != null) {
				this.publisher.publishEvent(event);
			}
		}

		private String getParentId(Span span) {
			return span.getParents() != null && !span.getParents().isEmpty() ? span
					.getParents().get(0) : null;
		}

		public void setHeader(HttpRequest.Builder builder, String name, String value) {
			if (value != null && isTracing()) {
				builder.header(name, value);
			}
		}
	}
}
