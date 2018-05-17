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

package org.springframework.cloud.sleuth.instrument.reactor.sample;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import brave.Tracer;
import brave.sampler.Sampler;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.sleuth.instrument.reactor.Issue866Configuration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import zipkin2.Span;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

// https://github.com/spring-cloud/spring-cloud-sleuth/issues/850
public class FlatMapTests {

	private static final Logger LOGGER = LoggerFactory.getLogger(FlatMapTests.class);

	@BeforeClass
	public static void setup() {
		Hooks.resetOnLastOperator();
		Schedulers.resetFactory();
		Issue866Configuration.hook = null;
	}

	@AfterClass
	public static void cleanup() {
		Issue866Configuration.hook = null;
	}

	@Rule public OutputCapture capture = new OutputCapture();

	@Test public void should_work_with_flat_maps() {
		//given
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				FlatMapTests.TestConfiguration.class, Issue866Configuration.class)
				.web(WebApplicationType.REACTIVE)
				.properties("server.port=0", "spring.jmx.enabled=false",
						"spring.application.name=TraceWebFluxTests", "security.basic.enabled=false",
						"management.security.enabled=false").run();
		ArrayListSpanReporter accumulator = context.getBean(ArrayListSpanReporter.class);
		int port = context.getBean(Environment.class).getProperty("local.server.port", Integer.class);
		RequestSender sender = context.getBean(RequestSender.class);
		TestConfiguration config = context.getBean(TestConfiguration.class);
		FactoryUser factoryUser = context.getBean(FactoryUser.class);
		sender.port = port;
		accumulator.clear();

		Awaitility.await().untilAsserted(() -> {
			//when
			accumulator.clear();
			String firstTraceId = flatMapTraceId(accumulator, callFlatMap(port).block());
			//then
			thenAllWebClientCallsHaveSameTraceId(firstTraceId, sender);
			thenSpanInFooHasSameTraceId(firstTraceId, config);
			accumulator.clear();

			//when
			String secondTraceId = flatMapTraceId(accumulator, callFlatMap(port).block());
			//then
			then(firstTraceId)
					.as("Id will not be reused between calls")
					.isNotEqualTo(secondTraceId);
			thenSpanInFooHasSameTraceId(secondTraceId, config);
			//and
			then(Arrays.stream(capture.toString().split("\n"))
					.filter(s -> s.contains("Received a request to uri"))
					.map(s -> s.split(",")[1])
					.collect(Collectors.toList()))
					.as("TracingFilter should not have any trace when receiving a request")
					.containsOnly("");
			//and #866
			then(factoryUser.wasSchedulerWrapped).isTrue();
		});
	}

	private void thenAllWebClientCallsHaveSameTraceId(String traceId,
			RequestSender sender) {
		then(sender.span.context().traceIdString()).isEqualTo(traceId);
	}

	private void thenSpanInFooHasSameTraceId(String traceId,
			TestConfiguration config) {
		then(config.spanInFoo.context().traceIdString()).isEqualTo(traceId);
	}

	private Mono<ClientResponse> callFlatMap(int port) {
		return WebClient.create().get()
				.uri("http://localhost:" + port + "/withFlatMap").exchange();
	}

	private String flatMapTraceId(ArrayListSpanReporter accumulator,
			ClientResponse response) {
		then(response.statusCode().value()).isEqualTo(200);
		then(accumulator.getSpans()).isNotEmpty();
		LOGGER.info("Accumulated spans: " + accumulator.getSpans());
		List<String> traceIdOfFlatMap = accumulator.getSpans().stream()
				.filter(span -> span.tags().containsKey("http.path") && span.tags()
						.get("http.path").equals("/withFlatMap")).map(Span::traceId)
				.collect(Collectors.toList());
		then(traceIdOfFlatMap).hasSize(1);
		return traceIdOfFlatMap.get(0);
	}

	@Configuration
	@EnableAutoConfiguration(
			exclude = { ReactiveUserDetailsServiceAutoConfiguration.class,
					ReactiveSecurityAutoConfiguration.class })
	static class TestConfiguration {

		brave.Span spanInFoo;

		@Bean RouterFunction<ServerResponse> handlers(Tracer tracer, RequestSender requestSender) {
			return route(GET("/noFlatMap"), request -> {
				LOGGER.info("noFlatMap");
				Flux<Integer> one = requestSender.getAll().map(string -> string.length());
				return ServerResponse.ok().body(one, Integer.class);
			}).andRoute(GET("/withFlatMap"), request -> {
				LOGGER.info("withFlatMap");
				Flux<Integer> one = requestSender.getAll().map(string -> string.length());
				Flux<Integer> response = one.flatMap(size -> requestSender.getAll()
						.doOnEach(sig -> LOGGER.info(sig.getContext().toString())))
						.map(string -> {
							LOGGER.info("WHATEVER YEAH");
							return string.length();
						});
				return ServerResponse.ok().body(response, Integer.class);
			}).andRoute(GET("/foo"), request -> {
				LOGGER.info("foo");
				spanInFoo = tracer.currentSpan();
				return ServerResponse.ok().body(Flux.just(1), Integer.class);
			});
		}

		@Bean WebClient webClient() {
			return WebClient.create();
		}

		@Bean ArrayListSpanReporter reporter() {
			return new ArrayListSpanReporter();
		}

		@Bean Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean RequestSender sender(WebClient client, Tracer tracer) {
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