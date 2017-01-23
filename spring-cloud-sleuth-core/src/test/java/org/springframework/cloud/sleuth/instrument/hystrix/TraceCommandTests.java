/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.hystrix;

import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;

import static com.netflix.hystrix.HystrixCommand.Setter.withGroupKey;
import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

public class TraceCommandTests {

	static final long EXPECTED_TRACE_ID = 1L;
	Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
			new DefaultSpanNamer(), new NoOpSpanLogger(), new NoOpSpanReporter());

	@Before
	public void setup() {
		HystrixPlugins.reset();
		TestSpanContextHolder.removeCurrentSpan();
	}

	@After
	public void cleanup() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work()
			throws Exception {
		Span firstSpanFromHystrix = givenACommandWasExecuted(traceReturningCommand());

		Span secondSpanFromHystrix = whenCommandIsExecuted(traceReturningCommand());

		then(secondSpanFromHystrix.getTraceId()).as("second trace id")
				.isNotEqualTo(firstSpanFromHystrix.getTraceId()).as("first trace id");
		then(secondSpanFromHystrix.getSavedSpan())
				.as("saved span as remnant of first span").isNull();
	}
	@Test
	public void should_create_a_local_span_with_proper_tags_when_hystrix_command_gets_executed()
			throws Exception {
		Span spanFromHystrix = whenCommandIsExecuted(traceReturningCommand());

		then(spanFromHystrix)
				.isALocalComponentSpan()
				.hasNameEqualTo("traceCommandKey")
				.hasATag("commandKey", "traceCommandKey");
	}

	@Test
	public void should_run_Hystrix_command_with_span_passed_from_parent_thread() {
		givenATraceIsPresentInTheCurrentThread();
		TraceCommand<Span> command = traceReturningCommand();

		Span spanFromCommand = whenCommandIsExecuted(command);

		then(spanFromCommand).as("Span from the Hystrix Thread")
				.isNotNull()
				.hasTraceIdEqualTo(EXPECTED_TRACE_ID)
				.hasATag("commandKey", "traceCommandKey")
				.hasATag("commandGroup", "group")
				.hasATag("threadPoolKey", "group");
	}

	@Test
	public void should_pass_tracing_information_when_using_Hystrix_commands() {
		Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				new DefaultSpanNamer(), new NoOpSpanLogger(), new NoOpSpanReporter());
		TraceKeys traceKeys = new TraceKeys();
		HystrixCommand.Setter setter = withGroupKey(asKey("group"))
				.andCommandKey(HystrixCommandKey.Factory.asKey("command"));
		// tag::hystrix_command[]
		HystrixCommand<String> hystrixCommand = new HystrixCommand<String>(setter) {
			@Override
			protected String run() throws Exception {
				return someLogic();
			}
		};
		// end::hystrix_command[]
		// tag::trace_hystrix_command[]
		TraceCommand<String> traceCommand = new TraceCommand<String>(tracer, traceKeys, setter) {
			@Override
			public String doRun() throws Exception {
				return someLogic();
			}
		};
		// end::trace_hystrix_command[]

		String resultFromHystrixCommand = hystrixCommand.execute();
		String resultFromTraceCommand = traceCommand.execute();

		then(resultFromHystrixCommand).isEqualTo(resultFromTraceCommand);
		then(tracer.getCurrentSpan()).isNull();
	}

	private String someLogic(){
		return "some logic";
	}

	private Span givenATraceIsPresentInTheCurrentThread() {
		return this.tracer.createSpan("http:test",
				Span.builder().traceId(EXPECTED_TRACE_ID).build());
	}

	private TraceCommand<Span> traceReturningCommand() {
		return new TraceCommand<Span>(this.tracer, new TraceKeys(),
				withGroupKey(asKey("group"))
						.andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties
								.Setter().withCoreSize(1).withMaxQueueSize(1))
						.andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
								.withExecutionTimeoutEnabled(false))
				.andCommandKey(HystrixCommandKey.Factory.asKey("traceCommandKey"))) {
			@Override
			public Span doRun() throws Exception {
				return TestSpanContextHolder.getCurrentSpan();
			}
		};
	}

	private Span whenCommandIsExecuted(TraceCommand<Span> command) {
		return command.execute();
	}

	private Span givenACommandWasExecuted(TraceCommand<Span> command) {
		return whenCommandIsExecuted(command);
	}
}