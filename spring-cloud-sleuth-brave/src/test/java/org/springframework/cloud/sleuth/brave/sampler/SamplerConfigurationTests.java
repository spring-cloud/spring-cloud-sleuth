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

package org.springframework.cloud.sleuth.brave.sampler;

import brave.Tracing;
import brave.TracingCustomizer;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Marcin Grzejszczak
 * @since
 */
// TODO: missing spring cloud context tests
public class SamplerConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SamplerConfiguration.class));

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
	void should_use_RateLimitedSampler_withTracingCustomizer() {
		this.contextRunner.withUserConfiguration(WithTracingCustomizer.class).run((context -> {
			final Sampler bean = context.getBean(Sampler.class);
			BDDAssertions.then(bean).isInstanceOf(RateLimitingSampler.class);
		}));
	}

	@Test
	void should_use_refresh_scope_sampler_when_no_property_passed_and_refresh_scope_present() {
		this.contextRunner.withUserConfiguration(WithTracingCustomizer.class, WithRefreshScope.class).run((context -> {
			BDDAssertions.then(context.containsBean("defaultTraceSampler")).as("refresh scope bean should be set")
					.isTrue();
			BDDAssertions.then(context.containsBean("defaultNonRefreshScopeTraceSampler"))
					.as("non refresh scope bean should not be picked").isFalse();
		}));
	}

	@Test
	void should_use_non_refresh_scope_sampler_when_property_passed_and_refresh_scope_present() {
		this.contextRunner.withUserConfiguration(WithTracingCustomizer.class, WithRefreshScope.class)
				.withPropertyValues("spring.sleuth.sampler.refresh.enabled=false").run((context -> {
					BDDAssertions.then(context.containsBean("defaultNonRefreshScopeTraceSampler"))
							.as("non refresh scope bean should be picked").isTrue();
					BDDAssertions.then(context.containsBean("defaultTraceSampler"))
							.as("refresh scope bean should not be set").isFalse();
				}));
	}

	@Test
	void samplerFromProps_probability() {
		SamplerProperties properties = new SamplerProperties();
		properties.setProbability(0.01f);

		Sampler sampler = SamplerConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isInstanceOf(brave.sampler.CountingSampler.class);
	}

	@Test
	void samplerFromProps_rateLimit() {
		SamplerProperties properties = new SamplerProperties();

		Sampler sampler = SamplerConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isInstanceOf(brave.sampler.RateLimitingSampler.class);
	}

	@Test
	void samplerFromProps_rateLimitZero() {
		SamplerProperties properties = new SamplerProperties();
		properties.setRate(0);

		Sampler sampler = SamplerConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isSameAs(Sampler.NEVER_SAMPLE);
	}

	@Test
	void samplerFromProps_prefersProbability() {
		SamplerProperties properties = new SamplerProperties();
		properties.setProbability(0.01f);
		properties.setRate(20);

		Sampler sampler = SamplerConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isInstanceOf(brave.sampler.CountingSampler.class);
	}

	@Test
	void samplerFromProps_prefersZeroProbability() {
		SamplerProperties properties = new SamplerProperties();
		properties.setProbability(0.0f);
		properties.setRate(20);

		Sampler sampler = SamplerConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isSameAs(Sampler.NEVER_SAMPLE);
	}

	@Configuration(proxyBeanMethods = false)
	static class WithSpanHandler {

		@Bean
		SpanHandler testSpanHandler() {
			return new SpanHandler() {
				@Override
				public boolean end(TraceContext context, MutableSpan span, Cause cause) {
					return true;
				}
			};
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class WithTracingCustomizer {

		@Bean
		TracingCustomizer tracingCustomizer() {
			return Tracing.Builder::toString;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class WithRefreshScope {

		@Bean
		RefreshScope refreshScope() {
			return new RefreshScope();
		}

	}

}
