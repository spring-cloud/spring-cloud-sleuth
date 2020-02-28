/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.ArrayList;
import java.util.List;

import brave.Tracing;
import brave.http.HttpRequest;
import brave.http.HttpRequestParser;
import brave.http.HttpResponseParser;
import brave.http.HttpSampler;
import brave.http.HttpTracing;
import brave.http.HttpTracingCustomizer;
import brave.sampler.SamplerFunction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} related to HTTP based communication.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(TraceWebAutoConfiguration.class)
@ConditionalOnProperty(name = "spring.sleuth.http.enabled", havingValue = "true",
		matchIfMissing = true)
@AutoConfigureAfter(TraceWebAutoConfiguration.class)
@EnableConfigurationProperties({ TraceKeys.class, SleuthHttpLegacyProperties.class })
public class TraceHttpAutoConfiguration {

	static final int TRACING_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

	@Autowired(required = false)
	List<HttpTracingCustomizer> httpTracingCustomizers = new ArrayList<>();

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	HttpTracing httpTracing(Tracing tracing, SkipPatternProvider provider,
			@Nullable @HttpClientRequestParser HttpRequestParser httpClientRequestParser,
			@Nullable @HttpClientResponseParser HttpResponseParser httpClientResponseParser,
			@Nullable brave.http.HttpClientParser clientParser,
			@Nullable @HttpServerRequestParser HttpRequestParser httpServerRequestParser,
			@Nullable @HttpServerResponseParser HttpResponseParser httpServerResponseParser,
			@Nullable brave.http.HttpServerParser serverParser,
			@HttpClientSampler SamplerFunction<HttpRequest> httpClientSampler,
			@Nullable @ServerSampler HttpSampler serverSampler,
			@Nullable @HttpServerSampler SamplerFunction<HttpRequest> httpServerSampler) {
		if (httpServerSampler == null) {
			httpServerSampler = serverSampler;
		}
		SamplerFunction<HttpRequest> combinedSampler = combineUserProvidedSamplerWithSkipPatternSampler(
				httpServerSampler, provider);
		HttpTracing.Builder builder = HttpTracing.newBuilder(tracing)
				.clientSampler(httpClientSampler).serverSampler(combinedSampler);

		if (clientParser != null) { // look for deprecated type first
			builder.clientParser(clientParser);
		}
		else {
			if (httpClientRequestParser != null) {
				builder.clientRequestParser(httpClientRequestParser);
			}
			if (httpClientResponseParser != null) {
				builder.clientResponseParser(httpClientResponseParser);
			}
		}

		if (serverParser != null) { // look for deprecated type first
			builder.serverParser(serverParser);
		}
		else {
			if (httpServerRequestParser != null) {
				builder.serverRequestParser(httpServerRequestParser);
			}
			if (httpServerResponseParser != null) {
				builder.serverResponseParser(httpServerResponseParser);
			}
		}

		for (HttpTracingCustomizer customizer : this.httpTracingCustomizers) {
			customizer.customize(builder);
		}
		return builder.build();
	}

	private SamplerFunction<HttpRequest> combineUserProvidedSamplerWithSkipPatternSampler(
			@Nullable SamplerFunction<HttpRequest> serverSampler,
			SkipPatternProvider provider) {
		SkipPatternHttpServerSampler skipPatternSampler = new SkipPatternHttpServerSampler(
				provider);
		if (serverSampler == null) {
			return skipPatternSampler;
		}
		return new CompositeHttpSampler(skipPatternSampler, serverSampler);
	}

	@Bean
	@ConditionalOnMissingBean(name = HttpClientRequestParser.NAME)
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled",
			havingValue = "true")
	HttpRequestParser sleuthHttpClientRequestParser(TraceKeys traceKeys) {
		return new SleuthHttpClientParser(traceKeys);
	}

	@Bean
	@ConditionalOnMissingBean(name = HttpServerRequestParser.NAME)
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled",
			havingValue = "true")
	HttpRequestParser sleuthHttpServerRequestParser(TraceKeys traceKeys) {
		return new SleuthHttpServerParser(traceKeys);
	}

	@Bean
	@ConditionalOnMissingBean(name = HttpServerResponseParser.NAME)
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled",
			havingValue = "true")
	HttpResponseParser sleuthHttpServerResponseParser(TraceKeys traceKeys) {
		return new SleuthHttpServerParser(traceKeys);
	}

	@Bean
	@ConditionalOnMissingBean(name = HttpClientSampler.NAME)
	SamplerFunction<HttpRequest> sleuthHttpClientSampler(
			@Nullable @ClientSampler HttpSampler sleuthClientSampler,
			SleuthWebProperties sleuthWebProperties) {
		if (sleuthClientSampler != null) {
			return sleuthClientSampler;
		}
		return new SkipPatternHttpClientSampler(sleuthWebProperties);
	}

}

/**
 * Composite Http Sampler.
 *
 * @author Adrian Cole
 */
class CompositeHttpSampler implements SamplerFunction<HttpRequest> {

	final SamplerFunction<HttpRequest> left;

	final SamplerFunction<HttpRequest> right;

	CompositeHttpSampler(SamplerFunction<HttpRequest> left,
			SamplerFunction<HttpRequest> right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public Boolean trySample(HttpRequest request) {
		// If either decision is false, return false
		Boolean leftDecision = this.left.trySample(request);
		if (Boolean.FALSE.equals(leftDecision)) {
			return false;
		}
		Boolean rightDecision = this.right.trySample(request);
		if (Boolean.FALSE.equals(rightDecision)) {
			return false;
		}
		// If either decision is null, return the other
		if (leftDecision == null) {
			return rightDecision;
		}
		if (rightDecision == null) {
			return leftDecision;
		}
		// Neither are null and at least one is true
		return rightDecision;
	}

}

/**
 * Http Sampler that looks at paths.
 *
 * @author Marcin Grzejszczak
 */
class SkipPatternHttpClientSampler implements SamplerFunction<HttpRequest> {

	private final SleuthWebProperties properties;

	SkipPatternHttpClientSampler(SleuthWebProperties properties) {
		this.properties = properties;
	}

	@Override
	public Boolean trySample(HttpRequest request) {
		String path = request.path();
		if (path == null) {
			return null;
		}
		return path.matches(this.properties.getClient().getSkipPattern()) ? false : null;
	}

}
