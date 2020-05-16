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

package org.springframework.cloud.sleuth.sampler;

import brave.Tracing;
import brave.TracingCustomizer;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Marcin Grzejszczak
 * @since
 */
// TODO: missing spring cloud context tests
public class SamplerAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SamplerAutoConfiguration.class));

	@Test
	void should_use_NEVER_SAMPLER_when_only_logging() {
		this.contextRunner.run((context -> {
			final Sampler bean = context.getBean(Sampler.class);
			BDDAssertions.then(bean).isSameAs(Sampler.NEVER_SAMPLE);
		}));
	}

	@Test
	void should_use_RateLimitedSampler_withSpanHandler() {
		this.contextRunner.withUserConfiguration(WithSpanHandler.class).run((context -> {
			final Sampler bean = context.getBean(Sampler.class);
			BDDAssertions.then(bean).isInstanceOf(RateLimitingSampler.class);
		}));
	}

	@Test
	void should_use_RateLimitedSampler_withReporter() {
		this.contextRunner.withUserConfiguration(WithReporter.class).run((context -> {
			final Sampler bean = context.getBean(Sampler.class);
			BDDAssertions.then(bean).isInstanceOf(RateLimitingSampler.class);
		}));
	}

	@Test
	void should_use_RateLimitedSampler_withTracingCustomizer() {
		this.contextRunner.withUserConfiguration(WithTracingCustomizer.class)
				.run((context -> {
					final Sampler bean = context.getBean(Sampler.class);
					BDDAssertions.then(bean).isInstanceOf(RateLimitingSampler.class);
				}));
	}

	@Test
	void should_override_sampler() {
		this.contextRunner.withUserConfiguration(WithReporter.class, WithSampler.class)
				.run((context -> {
					final Sampler bean = context.getBean(Sampler.class);
					BDDAssertions.then(bean).isSameAs(Sampler.ALWAYS_SAMPLE);
				}));
	}

	@Test
	void samplerFromProps_probability() {
		SamplerProperties properties = new SamplerProperties();
		properties.setProbability(0.01f);

		Sampler sampler = SamplerAutoConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isInstanceOf(brave.sampler.CountingSampler.class);
	}

	@Test
	void samplerFromProps_rateLimit() {
		SamplerProperties properties = new SamplerProperties();

		Sampler sampler = SamplerAutoConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isInstanceOf(brave.sampler.RateLimitingSampler.class);
	}

	@Test
	void samplerFromProps_rateLimitZero() {
		SamplerProperties properties = new SamplerProperties();
		properties.setRate(0);

		Sampler sampler = SamplerAutoConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isSameAs(Sampler.NEVER_SAMPLE);
	}

	@Test
	void samplerFromProps_prefersProbability() {
		SamplerProperties properties = new SamplerProperties();
		properties.setProbability(0.01f);
		properties.setRate(20);

		Sampler sampler = SamplerAutoConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isInstanceOf(brave.sampler.CountingSampler.class);
	}

	@Test
	void samplerFromProps_prefersZeroProbability() {
		SamplerProperties properties = new SamplerProperties();
		properties.setProbability(0.0f);
		properties.setRate(20);

		Sampler sampler = SamplerAutoConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isSameAs(Sampler.NEVER_SAMPLE);
	}

	@Configuration
	static class WithSpanHandler {

		@Bean
		SpanHandler spanHandler() {
			return new SpanHandler() {
				@Override
				public boolean end(TraceContext context, MutableSpan span, Cause cause) {
					return true;
				}
			};
		}

	}

	@Configuration
	static class WithReporter {

		@Bean
		Reporter<Span> spanReporter() {
			return zipkin2.Span::toString;
		}

	}

	@Configuration
	static class WithSampler {

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

	@Configuration
	static class WithTracingCustomizer {

		@Bean
		TracingCustomizer tracingCustomizer() {
			return Tracing.Builder::toString;
		}

	}

}
