/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.trace;

import java.lang.invoke.MethodHandles;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.log.Slf4jSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class CustomLoggerTests {

	CustomSpanLogger spanLogger = new CustomSpanLogger();
	Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
			new DefaultSpanNamer(), this.spanLogger, new NoOpSpanReporter(), new TraceKeys());

	// https://github.com/spring-cloud/spring-cloud-sleuth/issues/547
	// https://github.com/spring-cloud/spring-cloud-sleuth/issues/605
	@Test
	public void should_pass_baggage_to_custom_span_logger() {
		Span parent = Span.builder().spanId(1).traceId(2).baggage("FOO", "bar").build();
		parent.setBaggageItem("bAz", "baz");

		Span child = this.tracer.createSpan("child", parent);

		then(child).hasBaggageItem("foo", "bar");
		then(child.getBaggageItem("FoO")).isEqualTo("bar");
		then(this.spanLogger.called.get()).isTrue();
		TestSpanContextHolder.removeCurrentSpan();
	}
}

class CustomSpanLogger extends Slf4jSpanLogger {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	final AtomicBoolean called = new AtomicBoolean();

	public CustomSpanLogger() {
		super("");
	}

	@Override public void logStartedSpan(Span parent, Span span) {
		super.logStartedSpan(parent, span);
		called.set(true);
		then(parent).hasBaggageItem("foo", "bar");
		then(span).hasBaggageItem("foo", "bar");
		then(span).hasBaggageItem("baz", "baz");
		log.info("Baggage item foo=>bar found");
		log.info("Parent's baggage: " + parent.getBaggage());
		log.info("Child's baggage: " + span.getBaggage());
	}
}
