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

package org.springframework.cloud.sleuth.instrument.hystrix;

import java.util.concurrent.atomic.AtomicReference;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;
import org.assertj.core.api.BDDAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.hystrix.HystrixCommand.Setter.withGroupKey;
import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey;
import static org.assertj.core.api.BDDAssertions.then;

public class TraceCommandTests {

	StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();

	TestSpanHandler spans = new TestSpanHandler();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(currentTraceContext)
			.addSpanHandler(this.spans).sampler(Sampler.ALWAYS_SAMPLE).build();

	Tracer tracer = this.tracing.tracer();

	@Before
	public void setup() {
		HystrixPlugins.reset();
		this.spans.clear();
	}

	@After
	public void close() {
		this.tracing.close();
		this.currentTraceContext.close();
	}

	@Test
	public void should_remove_span_from_thread_local_after_finishing_work()
			throws Exception {
		Span firstSpanFromHystrix = givenACommandWasExecuted(traceReturningCommand());

		Span secondSpanFromHystrix = whenCommandIsExecuted(traceReturningCommand());

		then(secondSpanFromHystrix.context().traceId()).as("second trace id")
				.isNotEqualTo(firstSpanFromHystrix.context().traceId())
				.as("first trace id");
	}

	@Test
	public void should_create_a_local_span_with_proper_tags_when_hystrix_command_gets_executed()
			throws Exception {
		whenCommandIsExecuted(traceReturningCommand());

		then(this.spans).hasSize(1);
		then(this.spans.get(0).tags()).containsEntry("commandKey", "traceCommandKey");
		then(this.spans.get(0).finishTimestamp()).isGreaterThan(0L);
	}

	@Test
	public void should_run_Hystrix_command_with_span_passed_from_parent_thread() {
		Span span = this.tracer.nextSpan();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			TraceCommand<Span> command = traceReturningCommand();
			whenCommandIsExecuted(command);
		}
		finally {
			span.finish();
		}

		then(this.spans).hasSize(2);
		then(this.spans.get(0).traceId()).isEqualTo(span.context().traceIdString());
		then(this.spans.get(0).tags()).containsEntry("commandKey", "traceCommandKey")
				.containsEntry("commandGroup", "group")
				.containsEntry("threadPoolKey", "group");
	}

	@Test
	public void should_pass_tracing_information_when_using_Hystrix_commands() {
		Tracer tracer = this.tracer;
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
		TraceCommand<String> traceCommand = new TraceCommand<String>(tracer, setter) {
			@Override
			public String doRun() throws Exception {
				return someLogic();
			}
		};
		// end::trace_hystrix_command[]

		String resultFromHystrixCommand = hystrixCommand.execute();
		String resultFromTraceCommand = traceCommand.execute();

		then(resultFromHystrixCommand).isEqualTo(resultFromTraceCommand);
	}

	@Test
	public void should_pass_tracing_information_when_using_Hystrix_commands_with_fallback() {
		Tracer tracer = this.tracer;
		AtomicReference<Span> spanBeforeThrowingException = new AtomicReference<>();
		HystrixCommand.Setter setter = withGroupKey(asKey("group"))
				.andCommandKey(HystrixCommandKey.Factory.asKey("command"));
		TraceCommand<Span> traceCommand = new TraceCommand<Span>(tracer, setter) {
			@Override
			public Span doRun() throws Exception {
				spanBeforeThrowingException.set(tracer.currentSpan());
				throw new FooException();
			}

			@Override
			public Span doGetFallback() {
				return tracer.currentSpan();
			}

			@Override
			protected String getFallbackMethodName() {
				return super.getFallbackMethodName() + "_foobar";
			}
		};

		Span span = whenCommandIsExecuted(traceCommand);

		BDDAssertions.then(span.context().traceIdString())
				.isEqualTo(spanBeforeThrowingException.get().context().traceIdString());
		then(this.spans).hasSize(1);
		then(this.spans.get(0).traceId()).isEqualTo(span.context().traceIdString());
		then(this.spans.get(0).tags()).containsEntry("commandKey", "command")
				.containsEntry("commandGroup", "group")
				.containsEntry("threadPoolKey", "group")
				.containsEntry("fallbackMethodName", "getFallback_foobar");
	}

	private String someLogic() {
		return "some logic";
	}

	private TraceCommand<Span> traceReturningCommand() {
		return new TraceCommand<Span>(this.tracer, withGroupKey(asKey("group"))
				.andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter()
						.withCoreSize(1).withMaxQueueSize(1))
				.andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
						.withExecutionTimeoutEnabled(false))
				.andCommandKey(HystrixCommandKey.Factory.asKey("traceCommandKey"))) {
			@Override
			public Span doRun() throws Exception {
				return TraceCommandTests.this.tracer.currentSpan();
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

class FooException extends RuntimeException {

}
