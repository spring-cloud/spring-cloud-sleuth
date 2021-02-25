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

package org.springframework.cloud.sleuth.instrument.reactor.sample;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import brave.Tracer;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.OutputCaptureRule;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.sleuth.instrument.reactor.Issue866Configuration;
import org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfigurationAccessorConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

// https://github.com/spring-cloud/spring-cloud-sleuth/issues/850
public class FlatMapTests {

	private static final Logger LOGGER = LoggerFactory.getLogger(FlatMapTests.class);

	@Rule
	public OutputCaptureRule capture = new OutputCaptureRule();

	@BeforeClass
	public static void setup() {
		TraceReactorAutoConfigurationAccessorConfiguration.close();
		Issue866Configuration.hook = null;
	}

	@AfterClass
	public static void cleanup() {
		Issue866Configuration.hook = null;
	}

	@BeforeEach
	void before() {
		TraceReactorAutoConfigurationAccessorConfiguration.close();
	}

	@Test
	public void should_work_with_flat_maps_on_hooks_instrumentation() {
		// given
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				FlatMapTests.TestConfiguration.class, Issue866Configuration.class)
						.web(WebApplicationType.REACTIVE)
						.properties("server.port=0", "spring.jmx.enabled=false",
								"spring.application.name=TraceWebFluxOnHooksTests",
								"security.basic.enabled=false",
								"management.security.enabled=false")
						.run();
		assertReactorTracing(context);
	}

	@Test
	public void should_work_with_flat_maps_on_each_operator_instrumentation() {
		// given
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				FlatMapTests.TestConfiguration.class, Issue866Configuration.class)
						.web(WebApplicationType.REACTIVE)
						.properties("server.port=0", "spring.jmx.enabled=false",
								"spring.sleuth.reactor.decorate-hooks=false",
								"spring.sleuth.reactor.decorate-on-each=true",
								"spring.application.name=TraceWebFluxOnEachTests",
								"security.basic.enabled=false",
								"management.security.enabled=false")
						.run();
		assertReactorTracing(context);
	}

	@Test
	public void should_work_with_flat_maps_with_on_last_operator_instrumentation() {
		// given
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				FlatMapTests.TestConfiguration.class, Issue866Configuration.class)
						.web(WebApplicationType.REACTIVE)
						.properties("server.port=0", "spring.jmx.enabled=false",
								"spring.sleuth.reactor.decorate-hooks=false",
								"spring.sleuth.reactor.decorate-on-each=false",
								"spring.application.name=TraceWebFluxOnLastTests",
								"security.basic.enabled=false",
								"management.security.enabled=false")
						.run();
		assertReactorTracing(context);

		try {
			System.setProperty("spring.sleuth.reactor.decorate-hooks", "false");
			System.setProperty("spring.sleuth.reactor.decorate-on-each", "false");
			// trigger context refreshed
			context.getBean(ContextRefresher.class).refresh();
			assertReactorTracing(context);
		}
		finally {
			System.clearProperty("spring.sleuth.reactor.decorate-hooks");
			System.clearProperty("spring.sleuth.reactor.decorate-on-each");
		}
	}

	private void assertReactorTracing(ConfigurableApplicationContext context) {
		TestSpanHandler spans = context.getBean(TestSpanHandler.class);
		int port = context.getBean(Environment.class).getProperty("local.server.port",
				Integer.class);
		RequestSender sender = context.getBean(RequestSender.class);
		TestConfiguration config = context.getBean(TestConfiguration.class);
		FactoryUser factoryUser = context.getBean(FactoryUser.class);
		sender.port = port;
		spans.clear();

		Awaitility.await().untilAsserted(() -> {
			// when
			LOGGER.info("Start");
			spans.clear();
			String firstTraceId = flatMapTraceId(spans, callFlatMap(port).block());
			// then
			LOGGER.info("Checking first trace id");
			thenAllWebClientCallsHaveSameTraceId(firstTraceId, sender);
			thenSpanInFooHasSameTraceId(firstTraceId, config);
			spans.clear();
			LOGGER.info("All web client calls have same trace id");

			// when
			LOGGER.info("Second trace start");
			String secondTraceId = flatMapTraceId(spans, callFlatMap(port).block());
			// then
			then(firstTraceId).as("Id will not be reused between calls")
					.isNotEqualTo(secondTraceId);
			LOGGER.info("Id was not reused between calls");
			thenSpanInFooHasSameTraceId(secondTraceId, config);
			LOGGER.info("Span in Foo has same trace id");
			// and
			List<String> requestUri = Arrays.stream(this.capture.toString().split("\n"))
					.filter(s -> s.contains("Received a request to uri"))
					.map(s -> s.split(",")[1]).collect(Collectors.toList());
			LOGGER.info(
					"TracingFilter should not have any trace when receiving a request "
							+ requestUri);
			then(requestUri).as(
					"TracingFilter should not have any trace when receiving a request")
					.containsOnly("");
			// and #866
			then(factoryUser.wasSchedulerWrapped).isTrue();
			LOGGER.info("Factory was wrapped");
		});
	}

	private void thenAllWebClientCallsHaveSameTraceId(String traceId,
			RequestSender sender) {
		then(sender.span.context().traceIdString()).isEqualTo(traceId);
	}

	private void thenSpanInFooHasSameTraceId(String traceId, TestConfiguration config) {
		then(config.spanInFoo.context().traceIdString()).isEqualTo(traceId);
	}

	private Mono<ClientResponse> callFlatMap(int port) {
		return WebClient.create().get().uri("http://localhost:" + port + "/withFlatMap")
				.exchange();
	}

	private String flatMapTraceId(TestSpanHandler spans, ClientResponse response) {
		then(response.statusCode().value()).isEqualTo(200);
		then(spans).isNotEmpty();
		LOGGER.info("Accumulated spans: " + spans);
		List<String> traceIdOfFlatMap = spans.spans().stream()
				.filter(span -> span.tags().containsKey("http.path")
						&& span.tags().get("http.path").equals("/withFlatMap"))
				.map(MutableSpan::traceId).collect(Collectors.toList());
		then(traceIdOfFlatMap).hasSize(1);
		return traceIdOfFlatMap.get(0);
	}

	@Configuration
	@EnableAutoConfiguration
	static class TestConfiguration {

		brave.Span spanInFoo;

		@Bean
		RouterFunction<ServerResponse> handlers(Tracer tracer,
				RequestSender requestSender) {
			return route(GET("/noFlatMap"), request -> {
				LOGGER.info("noFlatMap [" + request + "]");
				Flux<Integer> one = requestSender.getAll().map(String::length);
				return ServerResponse.ok().body(one, Integer.class);
			}).andRoute(GET("/withFlatMap"), request -> {
				LOGGER.info("withFlatMap [" + request + "]");
				Flux<Integer> one = requestSender.getAll().map(String::length);
				Flux<Integer> response = one
						.flatMap(size -> requestSender.getAll().doOnEach(
								sig -> LOGGER.info(sig.getContext().toString())))
						.map(string -> {
							LOGGER.info("WHATEVER YEAH");
							return string.length();
						});
				return ServerResponse.ok().body(response, Integer.class);
			}).andRoute(GET("/foo"), request -> {
				LOGGER.info("foo [" + request + "]");
				this.spanInFoo = tracer.currentSpan();
				return ServerResponse.ok().body(Flux.just(1), Integer.class);
			});
		}

		@Bean
		WebClient webClient() {
			return WebClient.create();
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		RequestSender sender(WebClient client, Tracer tracer) {
			return new RequestSender(client, tracer);
		}

		// https://github.com/spring-cloud/spring-cloud-sleuth/issues/866
		@Bean
		FactoryUser factoryUser() {
			return new FactoryUser();
		}

	}

}

class FactoryUser {

	boolean wasSchedulerWrapped = false;

	FactoryUser() {
		Issue866Configuration.TestHook hook = Issue866Configuration.hook;
		this.wasSchedulerWrapped = hook != null && hook.executed;
	}

}
