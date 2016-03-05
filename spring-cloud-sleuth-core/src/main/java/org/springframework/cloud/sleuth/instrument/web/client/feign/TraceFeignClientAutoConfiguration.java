/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import static java.util.Collections.singletonList;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.cloud.netflix.feign.FeignAutoConfiguration;
import org.springframework.cloud.netflix.feign.support.ResponseEntityDecoder;
import org.springframework.cloud.netflix.feign.support.SpringDecoder;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.instrument.hystrix.SleuthHystrixAutoConfiguration;
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
import feign.Response;
import feign.codec.Decoder;

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
	private Tracer tracer;

	private final FeignRequestContext feignRequestContext = FeignRequestContext.getInstance();

	@Bean
	@Scope("prototype")
	@ConditionalOnClass(HystrixCommand.class)
	@ConditionalOnProperty(name = "feign.hystrix.enabled", matchIfMissing = true)
	public Feign.Builder feignHystrixBuilder(Tracer tracer, TraceKeys traceKeys) {
		return SleuthFeignBuilder.builder(tracer);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.sleuth.feign.processor.enabled", matchIfMissing = true)
	public FeignBeanPostProcessor feignBeanPostProcessor(Tracer tracer) {
		return new FeignBeanPostProcessor(tracer);
	}

	@Bean
	@Primary
	public Decoder feignDecoder(final Tracer tracer) {
		return new TraceFeignDecoder(tracer, new ResponseEntityDecoder(new SpringDecoder(this.messageConverters)) {
			@Override
			public Object decode(Response response, Type type)
					throws IOException, FeignException {
					return super.decode(Response.create(response.status(),
							response.reason(), headersWithTraceId(response.headers()),
							response.body()), type);
			}
		});
	}

	/**
	 * Sleuth {@link feign.RequestInterceptor} that either starts a new Span
	 * or continues an existing one if a retry takes place.
	 */
	@Bean
	public RequestInterceptor traceIdRequestInterceptor(Tracer tracer) {
		return new TraceFeignRequestInterceptor(tracer);
	}

	private Map<String, Collection<String>> headersWithTraceId(
			Map<String, Collection<String>> headers) {
		Map<String, Collection<String>> newHeaders = new HashMap<>();
		newHeaders.putAll(headers);
		Span span = this.feignRequestContext.getCurrentSpan();
		if (span == null) {
			setHeader(newHeaders, Span.NOT_SAMPLED_NAME, "true");
			return newHeaders;
		}
		setHeader(newHeaders, Span.TRACE_ID_NAME, span.getTraceId());
		setHeader(newHeaders, Span.SPAN_ID_NAME, span.getSpanId());
		setHeader(newHeaders, Span.PARENT_ID_NAME, getParentId(span));
		return newHeaders;
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
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

}
