/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.otel.bridge.http;

import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.api.SamplerFunction;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpRequest;
import org.springframework.cloud.sleuth.api.http.HttpRequestParser;
import org.springframework.cloud.sleuth.api.http.HttpResponseParser;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.instrument.web.HttpClientRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientResponseParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientSampler;
import org.springframework.cloud.sleuth.instrument.web.HttpServerRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpServerResponseParser;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternConfiguration;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.cloud.sleuth.instrument.web.SleuthWebProperties;
import org.springframework.cloud.sleuth.instrument.web.TraceHttpAutoConfiguration;
import org.springframework.cloud.sleuth.otel.bridge.TraceOtelBridgeAutoConfiguation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
@ConditionalOnBean({ Tracer.class, io.opentelemetry.api.trace.Tracer.class })
@AutoConfigureBefore(TraceHttpAutoConfiguration.class)
@AutoConfigureAfter({ TraceOtelBridgeAutoConfiguation.class, SkipPatternConfiguration.class })
public class TraceOtelHttpBridgeAutoConfiguration {

	@Bean
	HttpClientHandler otelHttpClientHandler(io.opentelemetry.api.trace.Tracer tracer,
			@Nullable @HttpClientRequestParser HttpRequestParser httpClientRequestParser,
			@Nullable @HttpClientResponseParser HttpResponseParser httpClientResponseParser,
			SamplerFunction<HttpRequest> samplerFunction) {
		return new OtelHttpClientHandler(tracer, httpClientRequestParser, httpClientResponseParser, samplerFunction);
	}

	@Bean
	HttpServerHandler otelHttpServerHandler(io.opentelemetry.api.trace.Tracer tracer,
			@Nullable @HttpServerRequestParser HttpRequestParser httpServerRequestParser,
			@Nullable @HttpServerResponseParser HttpResponseParser httpServerResponseParser,
			SkipPatternProvider skipPatternProvider) {
		return new OtelHttpServerHandler(tracer, httpServerRequestParser, httpServerResponseParser,
				skipPatternProvider);
	}

	@Bean
	@ConditionalOnMissingBean(name = HttpClientSampler.NAME)
	SamplerFunction<HttpRequest> defaultHttpClientSampler(SleuthWebProperties sleuthWebProperties) {
		String skipPattern = sleuthWebProperties.getClient().getSkipPattern();
		if (skipPattern == null) {
			return SamplerFunction.deferDecision();
		}
		return new SkipPatternSampler(Pattern.compile(skipPattern));
	}

}

class SkipPatternSampler implements SamplerFunction<HttpRequest> {

	private final Pattern pattern;

	SkipPatternSampler(Pattern pattern) {
		this.pattern = pattern;
	}

	@Override
	public final Boolean trySample(HttpRequest request) {
		String url = request.path();
		boolean shouldSkip = this.pattern.matcher(url).matches();
		if (shouldSkip) {
			return false;
		}
		return null;
	}

}
