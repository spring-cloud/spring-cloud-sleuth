/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.rsocket;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import io.rsocket.frame.FrameType;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketRequester.Builder;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.stereotype.Controller;
import org.springframework.util.MimeType;

import static org.assertj.core.api.BDDAssertions.then;

public abstract class TraceRSocketTests {

	public static final String EXPECTED_TRACE_ID = "b919095138aa4c6e";

	@Test
	public void should_instrument_responder() throws Exception {
		// setup
		ConfigurableApplicationContext context = new SpringApplicationBuilder(MyConfig.class, testConfiguration())
				.web(WebApplicationType.REACTIVE)
				.properties("server.port=0", "spring.rsocket.server.transport=websocket",
						"spring.rsocket.server.mapping-path=/rsocket", "spring.jmx.enabled=false",
						"spring.application.name=TraceRSocketTests", "security.basic.enabled=false",
						"management.security.enabled=false")
				.run();
		final TestSpanHandler spans = context.getBean(TestSpanHandler.class);
		final int port = context.getBean(Environment.class).getProperty("local.server.port", Integer.class);
		final TestController controller2 = context.getBean(TestController.class);
		final RSocketStrategies strategies = context.getBean(RSocketStrategies.class);

		final Builder rsocketRequesterBuilder = RSocketRequester.builder().rsocketStrategies(strategies);

		final RSocketRequester rSocketRequester = rsocketRequesterBuilder
				.websocket(URI.create("ws://localhost:" + port + "/rsocket"));

		// REQUEST FNF
		whenRequestFnFIsSent(rSocketRequester, "api.c2.fnf").block();

		FrameType receivedFrame = controller2.getReceivedFrames().take();
		thenSpanWasReportedWithTags(spans, "api.c2.fnf", receivedFrame);
		spans.clear();
		controller2.reset();

		// REQUEST RESPONSE
		whenRequestResponseIsSent(rSocketRequester, "api.c2.rr").block();

		receivedFrame = controller2.getReceivedFrames().take();
		thenSpanWasReportedWithTags(spans, "api.c2.rr", receivedFrame);
		spans.clear();
		controller2.reset();

		// REQUEST STREAM
		whenRequestStreamIsSent(rSocketRequester, "api.c2.rs").blockLast();

		receivedFrame = controller2.getReceivedFrames().take();
		thenSpanWasReportedWithTags(spans, "api.c2.rs", receivedFrame);
		spans.clear();
		controller2.reset();

		// REQUEST CHANNEL
		whenRequestChannelIsSent(rSocketRequester, "api.c2.rc").blockLast();

		receivedFrame = controller2.getReceivedFrames().take();
		thenSpanWasReportedWithTags(spans, "api.c2.rc", receivedFrame);
		spans.clear();
		controller2.reset();

		// REQUEST FNF
		whenNonSampledRequestFnfIsSent(rSocketRequester);
		controller2.getReceivedFrames().take();
		// then
		thenNoSpanWasReported(spans, controller2, expectedTraceId());
		spans.clear();
		controller2.reset();

		// REQUEST RESPONSE
		whenNonSampledRequestResponseIsSent(rSocketRequester);
		controller2.getReceivedFrames().take();
		// then
		thenNoSpanWasReported(spans, controller2, expectedTraceId());
		spans.clear();
		controller2.reset();

		// REQUEST STREAM
		whenNonSampledRequestStreamIsSent(rSocketRequester);
		controller2.getReceivedFrames().take();
		// then
		thenNoSpanWasReported(spans, controller2, expectedTraceId());
		spans.clear();
		controller2.reset();

		// REQUEST CHANNEL
		whenNonSampledRequestChannelIsSent(rSocketRequester);
		controller2.getReceivedFrames().take();
		// then
		thenNoSpanWasReported(spans, controller2, expectedTraceId());
		spans.clear();
		controller2.reset();

		// cleanup
		context.close();
	}

	protected String expectedTraceId() {
		return EXPECTED_TRACE_ID;
	}

	protected String expectedSpanId() {
		return EXPECTED_TRACE_ID;
	}

	@Test
	public void should_instrument_requester_and_responder() throws Exception {
		// setup
		ConfigurableApplicationContext context = new SpringApplicationBuilder(MyConfig.class, testConfiguration())
				.web(WebApplicationType.REACTIVE)
				.properties("server.port=0", "spring.rsocket.server.transport=websocket",
						"spring.rsocket.server.mapping-path=/rsocket", "spring.jmx.enabled=false",
						"spring.application.name=TraceRSocketTests", "security.basic.enabled=false",
						"management.security.enabled=false")
				.run();

		final org.springframework.cloud.sleuth.Tracer tracer = context
				.getBean(org.springframework.cloud.sleuth.Tracer.class);
		final TestSpanHandler spans = context.getBean(TestSpanHandler.class);
		final int port = context.getBean(Environment.class).getProperty("local.server.port", Integer.class);
		final TestController controller2 = context.getBean(TestController.class);

		final Builder rsocketRequesterBuilder = context.getBean(Builder.class);

		final RSocketRequester rSocketRequester = rsocketRequesterBuilder
				.websocket(URI.create("ws://localhost:" + port + "/rsocket"));

		// REQUEST FNF
		final org.springframework.cloud.sleuth.Span nextSpanFnf = tracer.nextSpan().start();
		whenRequestFnFIsSent(rSocketRequester, "api.c2.fnf")
				.contextWrite(ctx -> ctx.put(TraceContext.class, nextSpanFnf.context()))
				.doFinally(signalType -> nextSpanFnf.end()).block();
		controller2.getReceivedFrames().take();
		thenNoSpanWasReported(spans, controller2, nextSpanFnf.context().traceId());
		spans.clear();
		controller2.reset();

		// REQUEST RESPONSE
		final org.springframework.cloud.sleuth.Span nextSpanRR = tracer.nextSpan().start();
		whenRequestResponseIsSent(rSocketRequester, "api.c2.rr")
				.contextWrite(ctx -> ctx.put(TraceContext.class, nextSpanRR.context()))
				.doFinally(signalType -> nextSpanRR.end()).block();

		controller2.getReceivedFrames().take();
		thenNoSpanWasReported(spans, controller2, nextSpanRR.context().traceId());
		spans.clear();
		controller2.reset();

		// REQUEST STREAM
		final org.springframework.cloud.sleuth.Span nextSpanRS = tracer.nextSpan().start();
		whenRequestStreamIsSent(rSocketRequester, "api.c2.rs")
				.contextWrite(ctx -> ctx.put(TraceContext.class, nextSpanRS.context()))
				.doFinally(signalType -> nextSpanRS.end()).blockLast();

		controller2.getReceivedFrames().take();
		thenNoSpanWasReported(spans, controller2, nextSpanRS.context().traceId());
		spans.clear();
		controller2.reset();

		// REQUEST CHANNEL
		final org.springframework.cloud.sleuth.Span nextSpanRC = tracer.nextSpan().start();
		whenRequestChannelIsSent(rSocketRequester, "api.c2.rc")
				.contextWrite(ctx -> ctx.put(TraceContext.class, nextSpanRC.context()))
				.doFinally(signalType -> nextSpanRC.end()).blockLast();

		controller2.getReceivedFrames().take();
		thenNoSpanWasReported(spans, controller2, nextSpanRC.context().traceId());
		spans.clear();
		controller2.reset();

		// cleanup
		context.close();
	}

	protected abstract Class testConfiguration();

	private void thenSpanWasReportedWithTags(TestSpanHandler spans, String path, FrameType frameType) {
		String expectedName = frameType.name() + " " + path;
		Awaitility.await().untilAsserted(() -> {
			FinishedSpan span = spans.reportedSpans().stream()
					.filter(finished -> expectedName.equals(finished.getName())).findFirst()
					.orElseThrow(() -> new AssertionError("Span with name [" + expectedName + "] not found"));
			then(span.getTags()).containsEntry("messaging.controller.class",
					"org.springframework.cloud.sleuth.instrument.rsocket.TraceRSocketTests$TestController");
			then(span.getTags()).containsKey("messaging.controller.method");
		});
	}

	private Mono<Void> whenRequestFnFIsSent(RSocketRequester requester, String path) {
		return requester.route(path).send();
	}

	private Mono<String> whenRequestResponseIsSent(RSocketRequester requester, String path) {
		return requester.route(path).retrieveMono(String.class);
	}

	private Flux<String> whenRequestStreamIsSent(RSocketRequester requester, String path) {
		return requester.route(path).retrieveFlux(String.class);
	}

	private Flux<String> whenRequestChannelIsSent(RSocketRequester requester, String path) {
		return requester.route(path).data(Flux.fromArray(new String[] { "test1", "test2" })).retrieveFlux(String.class);
	}

	private void whenNonSampledRequestFnfIsSent(RSocketRequester requester) {
		requester.route("api.c2.fnf").metadata(expectedTraceId() + "-" + expectedSpanId() + "-0", new MimeType("b3") {
			@Override
			public String toString() {
				return "b3";
			}
		}).send().block();
	}

	private void whenNonSampledRequestResponseIsSent(RSocketRequester requester) {
		requester.route("api.c2.rr").metadata(expectedTraceId() + "-" + expectedSpanId() + "-0", new MimeType("b3") {
			@Override
			public String toString() {
				return "b3";
			}
		}).retrieveMono(String.class).block();
	}

	private void whenNonSampledRequestStreamIsSent(RSocketRequester requester) {
		requester.route("api.c2.rs").metadata(expectedTraceId() + "-" + expectedSpanId() + "-0", new MimeType("b3") {
			@Override
			public String toString() {
				return "b3";
			}
		}).retrieveFlux(String.class).blockLast();
	}

	private void whenNonSampledRequestChannelIsSent(RSocketRequester requester) {
		requester.route("api.c2.rc").metadata(expectedTraceId() + "-" + expectedSpanId() + "-0", new MimeType("b3") {
			@Override
			public String toString() {
				return "b3";
			}
		}).data(Flux.fromArray(new String[] { "test1", "test2" })).retrieveFlux(String.class).blockLast();
	}

	private void thenNoSpanWasReported(TestSpanHandler spans, TestController controller2, String expectedTraceId) {
		// then(spans).isEmpty(); // FIXME: does not work for request case
		Awaitility.await().untilAsserted(() -> {
			then(controller2.getSpan()).isNotNull();
			then(controller2.getSpan().context().traceId()).isEqualTo(expectedTraceId);
		});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class MyConfig {

		@Bean
		TestController controller(Tracer tracer) {
			return new TestController(tracer);
		}

	}

	@Controller
	@MessageMapping("api.c2")
	static class TestController {

		final Tracer tracer;

		Span span;

		ContextView interceptedContext;

		BlockingQueue<FrameType> receivedFrames = new LinkedBlockingDeque<>();

		TestController(Tracer tracer) {
			this.tracer = tracer;
		}

		BlockingQueue<FrameType> getReceivedFrames() {
			return this.receivedFrames;
		}

		Span getSpan() {
			return this.span;
		}

		void reset() {
			this.span = null;
		}

		@MessageMapping("fnf")
		Mono<Void> testFnf() {

			this.span = this.tracer.currentSpan();

			return Mono.deferContextual(c -> {
				interceptedContext = c;
				receivedFrames.offer(FrameType.REQUEST_FNF);
				return Mono.empty();
			});
		}

		@MessageMapping("rr")
		Mono<String> testRR() {
			this.span = this.tracer.currentSpan();

			return Mono.deferContextual(c -> {
				interceptedContext = c;
				receivedFrames.offer(FrameType.REQUEST_RESPONSE);
				return Mono.just("response");
			});
		}

		@MessageMapping("rs")
		Flux<String> testRS() {
			this.span = this.tracer.currentSpan();

			return Flux.deferContextual(c -> {
				interceptedContext = c;
				receivedFrames.offer(FrameType.REQUEST_STREAM);
				return Flux.just("stream");
			});
		}

		@MessageMapping("rc")
		Flux<String> testRC(@Payload Flux<String> inbound) {
			this.span = this.tracer.currentSpan();

			return Flux.deferContextual(c -> {
				interceptedContext = c;
				receivedFrames.offer(FrameType.REQUEST_CHANNEL);
				return inbound;
			});
		}

	}

}
