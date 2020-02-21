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

import brave.http.HttpRequest;
import brave.http.HttpTracing;
import brave.sampler.SamplerFunction;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

public class TraceHttpAutoConfigurationTests {

	@Test
	public void defaultsToSkipPatternHttpClientSampler() {
		contextRunner().run((context) -> {
			SamplerFunction<HttpRequest> clientSampler = context
					.getBean(HttpTracing.class).clientRequestSampler();

			then(clientSampler).isInstanceOf(SkipPatternHttpClientSampler.class);
		});
	}

	@Test
	public void configuresUserProvidedHttpClientSampler() {
		contextRunner().withUserConfiguration(HttpClientSamplerConfig.class)
				.run((context) -> {
					SamplerFunction<HttpRequest> clientSampler = context
							.getBean(HttpTracing.class).clientRequestSampler();

					then(clientSampler).isSameAs(HttpClientSamplerConfig.INSTANCE);
				});
	}

	@Test
	public void defaultsToSkipPatternHttpServerSampler() {
		contextRunner().run((context) -> {
			SamplerFunction<HttpRequest> serverSampler = context
					.getBean(HttpTracing.class).serverRequestSampler();

			then(serverSampler).isInstanceOf(SkipPatternHttpServerSampler.class);
		});
	}

	@Test
	public void wrapsUserProvidedHttpServerSampler() {
		contextRunner().withUserConfiguration(HttpServerSamplerConfig.class)
				.run(thenCompositeHttpServerSamplerOf(HttpServerSamplerConfig.INSTANCE));
	}

	private ContextConsumer<AssertableApplicationContext> thenCompositeHttpServerSamplerOf(
			SamplerFunction<HttpRequest> instance) {
		return (context) -> {

			SamplerFunction<HttpRequest> serverSampler = context
					.getBean(HttpTracing.class).serverRequestSampler();

			then(serverSampler).isInstanceOf(CompositeHttpSampler.class);

			then(((CompositeHttpSampler) serverSampler).left)
					.isInstanceOf(SkipPatternHttpServerSampler.class);
			then(((CompositeHttpSampler) serverSampler).right).isSameAs(instance);
		};
	}

	private ApplicationContextRunner contextRunner(String... propertyValues) {
		return new ApplicationContextRunner().withPropertyValues(propertyValues)
				.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class,
						TraceHttpAutoConfiguration.class,
						TraceWebAutoConfiguration.class));
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
