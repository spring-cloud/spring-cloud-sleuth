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

package org.springframework.cloud.sleuth.brave.instrument.web;

import brave.http.HttpRequest;
import brave.http.HttpRequestParser;
import brave.http.HttpResponseParser;
import brave.http.HttpTracing;
import brave.sampler.SamplerFunction;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.brave.autoconfig.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.HttpClientRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientResponseParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientSampler;
import org.springframework.cloud.sleuth.instrument.web.HttpServerRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpServerResponseParser;
import org.springframework.cloud.sleuth.instrument.web.HttpServerSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.BDDAssertions.then;

public class BraveHttpConfigurationTests {

	@Test
	public void defaultsClientSamplerToDefer() {
		contextRunner().run((context) -> {
			SamplerFunction<HttpRequest> clientSampler = context.getBean(HttpTracing.class).clientRequestSampler();

			then(clientSampler.trySample(mockHttpRequestForPath("foo"))).isNull();
		});
	}

	@Test
	public void configuresClientSkipPattern() throws Exception {
		contextRunner().withPropertyValues("spring.sleuth.web.client.skip-pattern=foo.*|bar.*").run((context) -> {
			SamplerFunction<HttpRequest> clientSampler = context.getBean(HttpTracing.class).clientRequestSampler();

			then(clientSampler.trySample(mockHttpRequestForPath("foo"))).isFalse();
			then(clientSampler.trySample(mockHttpRequestForPath("bar"))).isFalse();
			then(clientSampler.trySample(mockHttpRequestForPath("baz"))).isNull();
		});
	}

	private HttpRequest mockHttpRequestForPath(String path) {
		HttpRequest httpRequest = BDDMockito.mock(HttpRequest.class);
		BDDMockito.given(httpRequest.path()).willReturn(path);
		return httpRequest;
	}

	@Test
	public void configuresUserProvidedHttpClientSampler() {
		contextRunner().withUserConfiguration(HttpClientSamplerConfig.class).run((context) -> {
			SamplerFunction<HttpRequest> clientSampler = context.getBean(HttpTracing.class).clientRequestSampler();

			then(clientSampler.trySample(mockHttpRequestForPath("foo"))).isNull();
		});
	}

	@Test
	public void defaultsServerSamplerToSkipPattern() {
		contextRunner().withPropertyValues("spring.sleuth.web.skip-pattern=foo.*|bar.*").run((context) -> {
			SamplerFunction<HttpRequest> serverSampler = context.getBean(HttpTracing.class).serverRequestSampler();

			then(serverSampler.trySample(mockHttpRequestForPath("foo"))).isFalse();
			then(serverSampler.trySample(mockHttpRequestForPath("bar"))).isFalse();
			then(serverSampler.trySample(mockHttpRequestForPath("baz"))).isNull();
		});
	}

	@Test
	public void defaultsServerSamplerToDeferWhenSkipPatternCleared() {
		contextRunner().withPropertyValues("spring.sleuth.web.skip-pattern").run((context) -> {
			SamplerFunction<HttpRequest> clientSampler = context.getBean(HttpTracing.class).serverRequestSampler();

			then(clientSampler.trySample(mockHttpRequestForPath("foo"))).isNull();
		});
	}

	@Test
	public void defaultHttpClientParser() {
		contextRunner().run((context) -> {
			HttpRequestParser clientRequestParser = context.getBean(HttpTracing.class).clientRequestParser();
			HttpResponseParser clientResponseParser = context.getBean(HttpTracing.class).clientResponseParser();

			then(clientRequestParser).isInstanceOf(HttpRequestParser.Default.class);
			then(clientResponseParser).isInstanceOf(HttpResponseParser.Default.class);
		});
	}

	@Test
	public void configuresUserProvidedHttpClientParser() {
		contextRunner().withUserConfiguration(HttpClientParserConfig.class).run((context) -> {
			HttpRequestParser clientRequestParser = context.getBean(HttpTracing.class).clientRequestParser();
			HttpResponseParser clientResponseParser = context.getBean(HttpTracing.class).clientResponseParser();

			then(clientRequestParser).isSameAs(HttpClientParserConfig.REQUEST_PARSER);
			then(clientResponseParser).isSameAs(HttpClientParserConfig.RESPONSE_PARSER);
		});
	}

	@Test
	public void defaultHttpServerParser() {
		contextRunner().run((context) -> {
			HttpRequestParser serverRequestParser = context.getBean(HttpTracing.class).serverRequestParser();
			HttpResponseParser serverResponseParser = context.getBean(HttpTracing.class).serverResponseParser();

			then(serverRequestParser).isInstanceOf(HttpRequestParser.Default.class);
			then(serverResponseParser).isInstanceOf(HttpResponseParser.Default.class);
		});
	}

	@Test
	public void configuresUserProvidedHttpServerParser() {
		contextRunner().withUserConfiguration(HttpServerParserConfig.class).run((context) -> {
			HttpRequestParser serverRequestParser = context.getBean(HttpTracing.class).serverRequestParser();
			HttpResponseParser serverResponseParser = context.getBean(HttpTracing.class).serverResponseParser();

			then(serverRequestParser).isSameAs(HttpServerParserConfig.REQUEST_PARSER);
			then(serverResponseParser).isSameAs(HttpServerParserConfig.RESPONSE_PARSER);
		});
	}

	/**
	 * Shows bean aliases work to configure the same instance for both client and server
	 */
	@Test
	public void configuresUserProvidedHttpClientAndServerParser() {
		contextRunner().withUserConfiguration(HttpParserConfig.class).run((context) -> {
			HttpRequestParser serverRequestParser = context.getBean(HttpTracing.class).serverRequestParser();
			HttpResponseParser serverResponseParser = context.getBean(HttpTracing.class).serverResponseParser();
			HttpRequestParser clientRequestParser = context.getBean(HttpTracing.class).clientRequestParser();
			HttpResponseParser clientResponseParser = context.getBean(HttpTracing.class).clientResponseParser();

			then(clientRequestParser).isSameAs(HttpParserConfig.REQUEST_PARSER);
			then(clientResponseParser).isSameAs(HttpParserConfig.RESPONSE_PARSER);
			then(serverRequestParser).isSameAs(HttpParserConfig.REQUEST_PARSER);
			then(serverResponseParser).isSameAs(HttpParserConfig.RESPONSE_PARSER);
		});
	}

	@Test
	public void hasNoCycles() {
		contextRunner().withConfiguration(AutoConfigurations.of(BraveHttpConfiguration.class))
				.withInitializer(c -> ((GenericApplicationContext) c).setAllowCircularReferences(false))
				.run((context) -> {
					BDDAssertions.then(context.isRunning()).isEqualTo(true);
				});
	}

	private ApplicationContextRunner contextRunner(String... propertyValues) {
		return new ApplicationContextRunner().withPropertyValues(propertyValues)
				.withConfiguration(AutoConfigurations.of(BraveAutoConfiguration.class, BraveHttpConfiguration.class));
	}

}

@Configuration(proxyBeanMethods = false)
class HttpClientSamplerConfig {

	static final org.springframework.cloud.sleuth.api.SamplerFunction<org.springframework.cloud.sleuth.api.http.HttpRequest> INSTANCE = request -> null;

	@Bean(HttpClientSampler.NAME)
	org.springframework.cloud.sleuth.api.SamplerFunction<org.springframework.cloud.sleuth.api.http.HttpRequest> sleuthHttpClientSampler() {
		return INSTANCE;
	}

}

@Configuration(proxyBeanMethods = false)
class HttpServerSamplerConfig {

	static final org.springframework.cloud.sleuth.api.SamplerFunction<org.springframework.cloud.sleuth.api.http.HttpRequest> INSTANCE = request -> null;

	@Bean(HttpServerSampler.NAME)
	org.springframework.cloud.sleuth.api.SamplerFunction<org.springframework.cloud.sleuth.api.http.HttpRequest> sleuthHttpServerSampler() {
		return INSTANCE;
	}

}

@Configuration(proxyBeanMethods = false)
class HttpClientParserConfig {

	static final HttpRequestParser REQUEST_PARSER = (r, c, s) -> {
	};
	static final HttpResponseParser RESPONSE_PARSER = (r, c, s) -> {
	};

	@Bean(HttpClientRequestParser.NAME)
	HttpRequestParser sleuthHttpClientRequestParser() {
		return REQUEST_PARSER;
	}

	@Bean(HttpClientResponseParser.NAME)
	HttpResponseParser sleuthHttpClientResponseParser() {
		return RESPONSE_PARSER;
	}

}

@Configuration(proxyBeanMethods = false)
class HttpServerParserConfig {

	static final HttpRequestParser REQUEST_PARSER = (r, c, s) -> {
	};
	static final HttpResponseParser RESPONSE_PARSER = (r, c, s) -> {
	};

	@Bean(HttpServerRequestParser.NAME)
	HttpRequestParser sleuthHttpServerRequestParser() {
		return REQUEST_PARSER;
	}

	@Bean(HttpServerResponseParser.NAME)
	HttpResponseParser sleuthHttpServerResponseParser() {
		return RESPONSE_PARSER;
	}

}

@Configuration(proxyBeanMethods = false)
class HttpParserConfig {

	static final HttpRequestParser REQUEST_PARSER = (r, c, s) -> {
	};
	static final HttpResponseParser RESPONSE_PARSER = (r, c, s) -> {
	};

	@Bean(name = { HttpClientRequestParser.NAME, HttpServerRequestParser.NAME })
	HttpRequestParser sleuthHttpServerRequestParser() {
		return REQUEST_PARSER;
	}

	@Bean(name = { HttpClientResponseParser.NAME, HttpServerResponseParser.NAME })
	HttpResponseParser sleuthHttpServerResponseParser() {
		return RESPONSE_PARSER;
	}

}
