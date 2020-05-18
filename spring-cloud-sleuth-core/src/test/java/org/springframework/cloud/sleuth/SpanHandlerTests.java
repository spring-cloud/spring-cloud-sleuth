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

package org.springframework.cloud.sleuth;

import brave.Span;
import brave.Tracer;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.assertj.core.api.BDDAssertions;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpanHandlerTests.SpanHandlerAspectTestsConfig.class,
		webEnvironment = NONE)
public class SpanHandlerTests {

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracer tracer;

	@Test
	public void should_adjust_span_twice_before_reporting() {
		Span hello = this.tracer.nextSpan().name("hello").start();

		hello.finish();

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).name()).isEqualTo("foo bar");
	}

	@Configuration
	@EnableAutoConfiguration(exclude = IntegrationAutoConfiguration.class)
	static class SpanHandlerAspectTestsConfig {

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		// tag::spanHandler[]
		@Bean
		SpanHandler handlerOne() {
			return new SpanHandler() {
				@Override
				public boolean end(TraceContext traceContext, MutableSpan span,
						Cause cause) {
					span.name("foo");
					return true; // keep this span
				}
			};
		}

		@Bean
		SpanHandler handlerTwo() {
			return new SpanHandler() {
				@Override
				public boolean end(TraceContext traceContext, MutableSpan span,
						Cause cause) {
					span.name(span.name() + " bar");
					return true; // keep this span
				}
			};
		}
		// end::spanHandler[]

	}

}
