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
import brave.sampler.SamplerFunctions;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.brave.autoconfig.TraceBraveAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.HttpClientSampler;
import org.springframework.cloud.sleuth.instrument.web.HttpServerSampler;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.BDDAssertions.then;

public class TraceHttpAutoConfigurationTests {

	@Test
	public void defaultsClientSamplerToDefer() {
		contextRunner().run((context) -> {
			SamplerFunction<HttpRequest> clientSampler = context.getBean(HttpTracing.class).clientRequestSampler();

			then(clientSampler).isSameAs(SamplerFunctions.deferDecision());
		});
	}

	@Test
	public void configuresClientSkipPattern() throws Exception {
		contextRunner().withPropertyValues("spring.sleuth.web.client.skip-pattern=foo.*|bar.*").run((context) -> {
			SamplerFunction<HttpRequest> clientSampler = context.getBean(HttpTracing.class).clientRequestSampler();

			then(clientSampler).isInstanceOf(SkipPatternHttpClientSampler.class);
		});
	}

	@Test
	public void configuresUserProvidedHttpClientSampler() {
		contextRunner().withUserConfiguration(HttpClientSamplerConfig.class).run((context) -> {
			SamplerFunction<HttpRequest> clientSampler = context.getBean(HttpTracing.class).clientRequestSampler();

			then(clientSampler).isSameAs(HttpClientSamplerConfig.INSTANCE);
		});
	}

	@Test
	public void defaultsServerSamplerToSkipPattern() {
		contextRunner().run((context) -> {
			SamplerFunction<HttpRequest> serverSampler = context.getBean(HttpTracing.class).serverRequestSampler();

			then(serverSampler).isInstanceOf(SkipPatternHttpServerSampler.class);
		});
	}

	@Test
	public void defaultsServerSamplerToDeferWhenSkipPatternCleared() {
		contextRunner().withPropertyValues("spring.sleuth.web.skip-pattern").run((context) -> {
			SamplerFunction<HttpRequest> clientSampler = context.getBean(HttpTracing.class).serverRequestSampler();

			then(clientSampler).isSameAs(SamplerFunctions.deferDecision());
		});
	}

	// TODO: [OTEL] Fix this
	/*@Test
	public void wrapsUserProvidedHttpServerSampler() {
		contextRunner().withUserConfiguration(HttpServerSamplerConfig.class)
				.run(thenCompositeHttpServerSamplerOf(HttpServerSamplerConfig.INSTANCE));
	}

	private ContextConsumer<AssertableApplicationContext> thenCompositeHttpServerSamplerOf(
			SamplerFunction<HttpRequest> instance) {
		return (context) -> {

			SamplerFunction<HttpRequest> serverSampler = context.getBean(HttpTracing.class).serverRequestSampler();

			then(serverSampler).isInstanceOf(CompositeHttpSampler.class);

			then(((CompositeHttpSampler) serverSampler).left).isInstanceOf(SkipPatternHttpServerSampler.class);
			then(((CompositeHttpSampler) serverSampler).right).isSameAs(instance);
		};
	}*/

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
		contextRunner()
				.withConfiguration(
						AutoConfigurations.of(SkipPatternConfiguration.class, TraceHttpAutoConfiguration.class))
				.withInitializer(c -> ((GenericApplicationContext) c).setAllowCircularReferences(false))
				.run((context) -> {
					BDDAssertions.then(context.isRunning()).isEqualTo(true);
				});
	}

	private ApplicationContextRunner contextRunner(String... propertyValues) {
		return new ApplicationContextRunner().withPropertyValues(propertyValues).withConfiguration(
				AutoConfigurations.of(TraceAutoConfiguration.class, TraceBraveAutoConfiguration.class,
						TraceHttpAutoConfiguration.class, SkipPatternConfiguration.class));
	}

}

@Configuration
class HttpClientSamplerConfig {

	static final SamplerFunction<HttpRequest> INSTANCE = request -> null;

	@Bean(HttpClientSampler.NAME)
	SamplerFunction<HttpRequest> sleuthHttpClientSampler() {
		return INSTANCE;
	}

}

@Configuration
class HttpServerSamplerConfig {

	static final SamplerFunction<HttpRequest> INSTANCE = request -> null;

	@Bean(HttpServerSampler.NAME)
	SamplerFunction<HttpRequest> sleuthHttpServerSampler() {
		return INSTANCE;
	}

}

@Configuration
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

@Configuration
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

@Configuration
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
