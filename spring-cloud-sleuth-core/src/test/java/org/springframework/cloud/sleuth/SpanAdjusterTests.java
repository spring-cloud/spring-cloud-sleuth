/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth;

import brave.Span;
import brave.Tracer;
import brave.sampler.Sampler;
import org.assertj.core.api.BDDAssertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin2.reporter.Reporter;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpanAdjusterTests.SpanAdjusterAspectTestsConfig.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class SpanAdjusterTests {

	@Autowired ArrayListSpanReporter reporter;
	@Autowired Tracer tracer;

	@Test
	public void should_adjust_span_twice_before_reporting() {
		Span hello = this.tracer.nextSpan().name("hello").start();

		hello.finish();

		BDDAssertions.then(this.reporter.getSpans()).hasSize(1);
		BDDAssertions.then(this.reporter.getSpans().get(0).name()).isEqualTo("foo bar");
	}

	@Configuration
	@EnableAutoConfiguration
	static class SpanAdjusterAspectTestsConfig {
		@Bean Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean Reporter<zipkin2.Span> reporter() {
			return new ArrayListSpanReporter();
		}

		// tag::adjuster[]
		@Bean SpanAdjuster adjusterOne() {
			return span -> span.toBuilder().name("foo").build();
		}

		@Bean SpanAdjuster adjusterTwo() {
			return span -> span.toBuilder().name(span.name() + " bar").build();
		}
		// end::adjuster[]
	}
}