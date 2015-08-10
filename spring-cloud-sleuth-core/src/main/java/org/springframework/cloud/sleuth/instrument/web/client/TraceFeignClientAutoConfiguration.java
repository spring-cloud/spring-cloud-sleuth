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

import feign.*;
import feign.codec.Decoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.cloud.netflix.feign.support.ResponseEntityDecoder;
import org.springframework.cloud.netflix.feign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;
import static org.springframework.cloud.sleuth.TraceContextHolder.getCurrentSpan;

/**
 *
 * Configuration for ensuring that TraceID is set on the response
 *
 * @author Marcin Grzejszczak, 4financeIT
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.feign.client.enabled", matchIfMissing = true)
@ConditionalOnClass(Client.class)
public class TraceFeignClientAutoConfiguration {

	@Autowired
	private ObjectFactory<HttpMessageConverters> messageConverters;

	@Bean
	@Primary
	public Decoder feignDecoder() {
		return new ResponseEntityDecoder(new SpringDecoder(messageConverters)) {
			@Override
			public Object decode(Response response, Type type) throws IOException, FeignException {
				return super.decode(Response.create(response.status(), response.reason(), headersWithTraceId(response.headers()), response.body()), type);
			}
		};
	}

	@Bean
	public RequestInterceptor traceIdRequestInterceptor() {
		return new RequestInterceptor() {
			@Override
			public void apply(RequestTemplate template) {
				if (!template.headers().containsKey(TRACE_ID_NAME)) {
					template.header(TRACE_ID_NAME, getCurrentSpan().getTraceId());
				}
			}
		};
	}

	private Map<String, Collection<String>> headersWithTraceId(Map<String, Collection<String>> headers) {
		Map<String, Collection<String>> newHeaders = new HashMap<>();
		newHeaders.putAll(headers);
		if (!headers.containsKey(TRACE_ID_NAME)) {
			newHeaders.put(TRACE_ID_NAME, singletonList(getCurrentSpan().getTraceId()));
		}
		return newHeaders;
	}

}
