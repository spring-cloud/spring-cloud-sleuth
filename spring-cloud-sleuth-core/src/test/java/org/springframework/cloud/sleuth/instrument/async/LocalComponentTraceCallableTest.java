/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.Random;

import org.junit.Test;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@SuppressWarnings("unchecked")
public class LocalComponentTraceCallableTest {

	Span closedSpan;
	Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
			new DefaultSpanNamer(), new NoOpSpanLogger(), new NoOpSpanReporter(), new TraceKeys()) {
		@Override public Span close(Span span) {
			LocalComponentTraceCallableTest.this.closedSpan = span;
			return super.close(span);
		}
	};

	@Test
	public void should_delegate_to_callable_wrapped_in_a_local_component() throws Exception {
		SpanContinuingTraceCallable<String> callable = new SpanContinuingTraceCallable<>(this.tracer, new TraceKeys(), new DefaultSpanNamer(),
				() -> "hello");

		String response = callable.call();

		then(response).isEqualTo("hello");
		then(this.closedSpan).isALocalComponentSpan();
	}

}