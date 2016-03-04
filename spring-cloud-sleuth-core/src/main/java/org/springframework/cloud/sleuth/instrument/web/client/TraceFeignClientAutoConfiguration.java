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

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.cloud.netflix.feign.FeignAutoConfiguration;
import org.springframework.cloud.netflix.feign.support.ResponseEntityDecoder;
import org.springframework.cloud.netflix.feign.support.SpringDecoder;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.instrument.hystrix.SleuthHystrixAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.hystrix.SleuthHystrixConcurrencyStrategy;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.util.StringUtils;

import com.netflix.hystrix.HystrixCommand;

import feign.Client;
import feign.Feign;
import feign.FeignException;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.Decoder;
import feign.hystrix.HystrixFeign;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables span information propagation when using Feign.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.feign.enabled", matchIfMissing = true)
@ConditionalOnClass(Client.class)
@AutoConfigureBefore(FeignAutoConfiguration.class)
@AutoConfigureAfter(SleuthHystrixAutoConfiguration.class)
public class TraceFeignClientAutoConfiguration {

	@Autowired
	private ObjectFactory<HttpMessageConverters> messageConverters;

	@Autowired
	private ApplicationEventPublisher publisher;

	@Autowired
	private Tracer tracer;

	@Bean
	@Scope("prototype")
	@ConditionalOnClass(HystrixCommand.class)
	@ConditionalOnMissingBean(SleuthHystrixConcurrencyStrategy.class)
	@ConditionalOnProperty(name = "feign.hystrix.enabled", matchIfMissing = true)
	public Feign.Builder feignHystrixBuilder(Tracer tracer, TraceKeys traceKeys) {
		return HystrixFeign.builder().invocationHandlerFactory(
				new SleuthHystrixInvocationHandler.Factory(tracer, traceKeys));
	}

	@Bean
	@Primary
	public Decoder feignDecoder() {
		return new ResponseEntityDecoder(new SpringDecoder(this.messageConverters)) {
			@Override
			public Object decode(Response response, Type type)
					throws IOException, FeignException {
				try {
					return super.decode(Response.create(response.status(),
							response.reason(), headersWithTraceId(response.headers()),
							response.body()), type);
				}
				finally {
					Span span = getCurrentSpan();
					if (span != null) {
						publish(new ClientReceivedEvent(this, span));
						TraceFeignClientAutoConfiguration.this.tracer.close(span);
					}
				}
			}
		};
	}

	@Bean
	public RequestInterceptor traceIdRequestInterceptor() {
		return new RequestInterceptor() {
			@Override
			public void apply(RequestTemplate template) {
				URI uri = URI.create(template.url());
				String spanName = uriScheme(uri) + ":" + uri.getPath();
				Span span = TraceFeignClientAutoConfiguration.this.tracer.createSpan(spanName);
				if (span == null) {
					setHeader(template, Span.NOT_SAMPLED_NAME, "true");
					return;
				}
				template.header(Span.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
				setHeader(template, Span.SPAN_NAME_NAME, span.getName());
				setHeader(template, Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
				if (!span.isExportable()) {
					setHeader(template, Span.NOT_SAMPLED_NAME, "true");
				}
				Long parentId = getParentId(span);
				if (parentId != null) {
					setHeader(template, Span.PARENT_ID_NAME, Span.idToHex(parentId));
				}
				setHeader(template, Span.PROCESS_ID_NAME, span.getProcessId());
				publish(new ClientSentEvent(this, span));
			}
		};
	}

	private String uriScheme(URI uri) {
		return uri.getScheme() == null ? "http" : uri.getScheme();
	}

	private void publish(ApplicationEvent event) {
		if (this.publisher != null) {
			this.publisher.publishEvent(event);
		}
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
	}

	public void setHeader(RequestTemplate request, String name, String value) {
		if (StringUtils.hasText(value) && !request.headers().containsKey(name)
				&& this.tracer.isTracing()) {
			request.header(name, value);
		}
	}

	private Map<String, Collection<String>> headersWithTraceId(
			Map<String, Collection<String>> headers) {
		Map<String, Collection<String>> newHeaders = new HashMap<>();
		newHeaders.putAll(headers);
		Span span = getCurrentSpan();
		if (span == null) {
			setHeader(newHeaders, Span.NOT_SAMPLED_NAME, "true");
			return newHeaders;
		}
		setHeader(newHeaders, Span.TRACE_ID_NAME, span.getTraceId());
		setHeader(newHeaders, Span.SPAN_ID_NAME, span.getSpanId());
		setHeader(newHeaders, Span.PARENT_ID_NAME, getParentId(span));
		return newHeaders;
	}

	public void setHeader(Map<String, Collection<String>> headers, String name,
			String value) {
		if (StringUtils.hasText(value) && !headers.containsKey(name)
				&& this.tracer.isTracing()) {
			headers.put(name, singletonList(value));
		}
	}

	public void setHeader(Map<String, Collection<String>> headers, String name,
			Long value) {
		if (value != null) {
			setHeader(headers, name, Span.idToHex(value));
		}
	}

	private Span getCurrentSpan() {
		return this.tracer.getCurrentSpan();
	}

}
