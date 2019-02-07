/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.propagation;

import java.util.List;
import java.util.Map;

import brave.ScopedSpan;
import brave.Tracer;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Taras Danylchuk
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
		"spring.sleuth.baggage-keys=my-baggage",
		"spring.sleuth.propagation-keys=my-propagation,others-propagation",
		"spring.sleuth.propagation.tag.whitelisted-keys=my-baggage,my-propagation" }, classes = TagPropagationFinishedSpanHandlerTest.TestConfiguration.class)
public class TagPropagationFinishedSpanHandlerTest {

	private static final String BAGGAGE_KEY = "my-baggage";

	private static final String BAGGAGE_VALUE = "332323";

	private static final String PROPAGATION_KEY = "my-propagation";

	private static final String PROPAGATION_VALUE = "332323";

	@Autowired
	private Tracer tracer;

	@Autowired
	private ArrayListSpanReporter arrayListSpanReporter;

	private ScopedSpan span;

	@Before
	public void setUp() {
		this.arrayListSpanReporter.clear();
		this.span = this.tracer.startScopedSpan("my-scoped-span");
		TraceContext context = this.span.context();
		ExtraFieldPropagation.set(context, BAGGAGE_KEY, BAGGAGE_VALUE);
		ExtraFieldPropagation.set(context, PROPAGATION_KEY, PROPAGATION_VALUE);
		ExtraFieldPropagation.set(context, "others-propagation", "some value");
	}

	@Test
	public void shouldReportWithBaggageInTags() {
		this.span.finish();

		List<zipkin2.Span> spans = this.arrayListSpanReporter.getSpans();
		assertThat(spans).hasSize(1);
		Map<String, String> tags = spans.get(0).tags();
		assertThat(tags).hasSize(2);
		assertThat(tags).containsEntry(BAGGAGE_KEY, BAGGAGE_VALUE);
		assertThat(tags).containsEntry(PROPAGATION_KEY, PROPAGATION_VALUE);
	}

	@Configuration
	@EnableAutoConfiguration
	public static class TestConfiguration {

		@Bean
		public ArrayListSpanReporter arrayListSpanReporter() {
			return new ArrayListSpanReporter();
		}

		@Bean
		public Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

}
