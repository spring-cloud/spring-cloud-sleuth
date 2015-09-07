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

package org.springframework.cloud.sleuth.instrument.web.client;

import static java.util.Collections.singletonList;
import static org.springframework.cloud.sleuth.Trace.NOT_SAMPLED_NAME;
import static org.springframework.cloud.sleuth.Trace.PARENT_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;
import static org.springframework.cloud.sleuth.TraceContextHolder.getCurrentSpan;
import static org.springframework.cloud.sleuth.TraceContextHolder.isTracing;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.cloud.netflix.feign.support.ResponseEntityDecoder;
import org.springframework.cloud.netflix.feign.support.SpringDecoder;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import feign.Client;
import feign.FeignException;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.Decoder;

/**
 *
 * Configuration for ensuring that TraceID is set on the response
 *
 * @author Marcin Grzejszczak, 4financeIT
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.feign.enabled", matchIfMissing = true)
@ConditionalOnClass(Client.class)
public class TraceFeignClientAutoConfiguration {

	@Autowired
	private ObjectFactory<HttpMessageConverters> messageConverters;

	@Autowired
	private ApplicationEventPublisher publisher;

	@Bean
	@Primary
	public Decoder feignDecoder() {
		return new ResponseEntityDecoder(new SpringDecoder(messageConverters)) {
			@Override
			public Object decode(Response response, Type type) throws IOException, FeignException {
				return super.decode(Response.create(response.status(), response.reason(),
						headersWithTraceId(response.headers()), response.body()), type);
			}
		};
	}

	@Bean
	public RequestInterceptor traceIdRequestInterceptor() {
		return new RequestInterceptor() {
			@Override
			public void apply(RequestTemplate template) {
				Span span = getCurrentSpan();
				if (span != null) {
					template.header(TRACE_ID_NAME, span.getTraceId());
					setHeader(template, TRACE_ID_NAME, span.getTraceId());
					setHeader(template, SPAN_ID_NAME, span.getSpanId());
					setHeader(template, PARENT_ID_NAME, getParentId(span));
					publish(new ClientSentEvent(this, span));
				}
			}
		};
	}

	private void publish(ApplicationEvent event) {
		if (this.publisher != null) {
			this.publisher.publishEvent(event);
		}
	}

	private String getParentId(Span span) {
		return span.getParents() != null && !span.getParents().isEmpty() ? span.getParents().get(0) : null;
	}

	public void setHeader(RequestTemplate request, String name, String value) {
		if (value != null && !request.headers().containsKey(name) && isTracing()) {
			request.header(name, value);
		}
	}

	private Map<String, Collection<String>> headersWithTraceId(Map<String, Collection<String>> headers) {
		Map<String, Collection<String>> newHeaders = new HashMap<>();
		newHeaders.putAll(headers);
		if (getCurrentSpan() == null) {
			setHeader(newHeaders, NOT_SAMPLED_NAME, "");
			return newHeaders;
		}
		setHeader(newHeaders, TRACE_ID_NAME, getCurrentSpan().getTraceId());
		setHeader(newHeaders, SPAN_ID_NAME, getCurrentSpan().getSpanId());
		setHeader(newHeaders, PARENT_ID_NAME, getParentId(getCurrentSpan()));
		return newHeaders;
	}

	public void setHeader(Map<String, Collection<String>> headers, String name, String value) {
		if (value != null && !headers.containsKey(name) && isTracing()) {
			headers.put(name, singletonList(value));
		}
	}
}
