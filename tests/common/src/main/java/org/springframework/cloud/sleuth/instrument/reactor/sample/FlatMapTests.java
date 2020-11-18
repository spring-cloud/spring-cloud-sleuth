/*
 * Copyright 2013-2020 the original author or authors.
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
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.instrument.reactor.Issue866Configuration;
import org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfigurationAccessorConfiguration;
import org.springframework.cloud.sleuth.instrument.web.WebFluxSleuthOperators;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

// https://github.com/spring-cloud/spring-cloud-sleuth/issues/850
@ExtendWith(OutputCaptureExtension.class)
public abstract class FlatMapTests {

	private static final Logger LOGGER = LoggerFactory.getLogger(FlatMapTests.class);

	@BeforeAll
	public static void setup() {
		TraceReactorAutoConfigurationAccessorConfiguration.close();
		Issue866Configuration.hook = null;
	}

	@AfterAll
	public static void cleanup() {
		Issue866Configuration.hook = null;
	}

	@Test
	public void should_work_with_flat_maps(CapturedOutput capture) {
		// given
		ConfigurableApplicationContext context = new SpringApplicationBuilder(FlatMapTests.TestConfiguration.class,
				testConfiguration(), Issue866Configuration.class)
						.web(WebApplicationType.REACTIVE)
						.properties("server.port=0", "spring.jmx.enabled=false",
								"spring.application.name=TraceWebFluxTests", "security.basic.enabled=false",
								"management.security.enabled=false")
						.run();
		assertReactorTracing(context, capture, () -> context.getBean(TestConfiguration.class).spanInFoo);
	}

	protected abstract Class testConfiguration();

	@Test
	public void should_work_with_flat_maps_with_on_last_operator_instrumentation(CapturedOutput capture) {
		// given
		ConfigurableApplicationContext context = new SpringApplicationBuilder(FlatMapTests.TestConfiguration.class,
				testConfiguration(), Issue866Configuration.class)
						.web(WebApplicationType.REACTIVE)
						.properties("server.port=0", "spring.jmx.enabled=false",
								"spring.sleuth.reactor.decorate-on-each=false",
								"spring.application.name=TraceWebFlux2Tests", "security.basic.enabled=false",
								"management.security.enabled=false")
						.run();
		assertReactorTracing(context, capture, () -> context.getBean(TestConfiguration.class).spanInFoo);
	}

	@Test
	public void should_work_with_flat_maps_with_on_manual_operator_instrumentation(CapturedOutput capture) {
		// given
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				FlatMapTests.TestManualConfiguration.class, testConfiguration(), Issue866Configuration.class)
						.web(WebApplicationType.REACTIVE)
						.properties("server.port=0", "spring.jmx.enabled=false",
								"spring.sleuth.reactor.instrumentation-type=MANUAL",
								"spring.application.name=TraceWebFlux3Tests", "security.basic.enabled=false",
								"management.security.enabled=false")
						.run();
		assertReactorTracing(context, capture, () -> context.getBean(TestManualConfiguration.class).spanInFoo);
	}

	private void assertReactorTracing(ConfigurableApplicationContext context, CapturedOutput capture,
			SpanProvider spanProvider) {
		TestSpanHandler spans = context.getBean(TestSpanHandler.class);
		int port = context.getBean(Environment.class).getProperty("local.server.port", Integer.class);
		RequestSender sender = context.getBean(RequestSender.class);
		FactoryUser factoryUser = context.getBean(FactoryUser.class);
		sender.port = port;
		spans.clear();

		Awaitility.await().atMost(5, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
			// when
			LOGGER.info("Start");
			spans.clear();
			String firstTraceId = flatMapTraceId(spans, callFlatMap(port).block());
			// then
			LOGGER.info("Checking first trace id");
			thenAllWebClientCallsHaveSameTraceId(firstTraceId, sender);
			thenSpanInFooHasSameTraceId(firstTraceId, spanProvider);
			spans.clear();
			LOGGER.info("All web client calls have same trace id");

			// when
			LOGGER.info("Second trace start");
			String secondTraceId = flatMapTraceId(spans, callFlatMap(port).block());
			// then
			then(firstTraceId).as("Id will not be reused between calls").isNotEqualTo(secondTraceId);
			LOGGER.info("Id was not reused between calls");
			thenSpanInFooHasSameTraceId(secondTraceId, spanProvider);
			LOGGER.info("Span in Foo has same trace id");
			// and
			List<String> requestUri = Arrays.stream(capture.toString().split("\n"))
					.filter(s -> s.contains("Received a request to uri")).map(s -> s.split(",")[1])
					.collect(Collectors.toList());
			LOGGER.info("TracingFilter should not have any trace when receiving a request " + requestUri);
			then(requestUri).as("TracingFilter should not have any trace when receiving a request").containsOnly("");
			// and #866
			then(factoryUser.wasSchedulerWrapped).isTrue();
			LOGGER.info("Factory was wrapped");
		});
	}

	private void thenAllWebClientCallsHaveSameTraceId(String traceId, RequestSender sender) {
		then(sender.span.context().traceId()).isEqualTo(traceId);
	}

	private void thenSpanInFooHasSameTraceId(String traceId, SpanProvider spanProvider) {
		then(spanProvider.get().context().traceId()).isEqualTo(traceId);
	}

	private Mono<ClientResponse> callFlatMap(int port) {
		return WebClient.create().get().uri("http://localhost:" + port + "/withFlatMap").exchange();
	}

	private String flatMapTraceId(TestSpanHandler spans, ClientResponse response) {
		then(response.statusCode().value()).isEqualTo(200);
		then(spans).isNotEmpty();
		LOGGER.info("Accumulated spans: " + spans);
		List<String> traceIdOfFlatMap = spans.reportedSpans().stream()
				.filter(span -> span.getTags().containsKey("http.path")
						&& span.getTags().get("http.path").equals("/withFlatMap"))
				.map(FinishedSpan::getTraceId).collect(Collectors.toList());
		then(traceIdOfFlatMap).hasSize(1);
		return traceIdOfFlatMap.get(0);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class TestConfiguration {

		Span spanInFoo;

		@Bean
		RouterFunction<ServerResponse> handlers(Tracer tracer, RequestSender requestSender) {
			return route(GET("/noFlatMap"), request -> {
				LOGGER.info("noFlatMap");
				Flux<Integer> one = requestSender.getAll().map(String::length);
				return ServerResponse.ok().body(one, Integer.class);
			}).andRoute(GET("/withFlatMap"), request -> {
				LOGGER.info("withFlatMap");
				Flux<Integer> one = requestSender.getAll().map(String::length);
				Flux<Integer> response = one.flatMap(
						size -> requestSender.getAll().doOnEach(sig -> LOGGER.info(sig.getContext().toString())))
						.map(string -> {
							LOGGER.info("WHATEVER YEAH");
							return string.length();
						});
				return ServerResponse.ok().body(response, Integer.class);
			}).andRoute(GET("/foo"), request -> {
				LOGGER.info("foo");
				this.spanInFoo = tracer.currentSpan();
				return ServerResponse.ok().body(Flux.just(1), Integer.class);
			});
		}

		@Bean
		WebClient webClient() {
			return WebClient.create();
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

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class TestManualConfiguration {

		Span spanInFoo;

		@Bean
		RouterFunction<ServerResponse> handlers(org.springframework.cloud.sleuth.api.Tracer tracing,
				CurrentTraceContext currentTraceContext, ManualRequestSender requestSender) {
			return route(GET("/noFlatMap"), request -> {
				ServerWebExchange exchange = request.exchange();
				WebFluxSleuthOperators.withSpanInScope(tracing, currentTraceContext, exchange,
						() -> LOGGER.info("noFlatMap"));
				Flux<Integer> one = requestSender.getAll().map(String::length);
				return ServerResponse.ok().body(one, Integer.class);
			}).andRoute(GET("/withFlatMap"), request -> {
				ServerWebExchange exchange = request.exchange();
				WebFluxSleuthOperators.withSpanInScope(tracing, currentTraceContext, exchange,
						() -> LOGGER.info("withFlatMap"));
				Flux<Integer> one = requestSender.getAll().map(String::length);
				Flux<Integer> response = one
						.flatMap(size -> requestSender.getAll().doOnEach(sig -> WebFluxSleuthOperators
								.withSpanInScope(sig.getContext(), () -> LOGGER.info(sig.getContext().toString()))))
						.map(string -> {
							WebFluxSleuthOperators.withSpanInScope(tracing, currentTraceContext, exchange,
									() -> LOGGER.info("WHATEVER YEAH"));
							return string.length();
						});
				return ServerResponse.ok().body(response, Integer.class);
			}).andRoute(GET("/foo"), request -> {
				ServerWebExchange exchange = request.exchange();
				WebFluxSleuthOperators.withSpanInScope(tracing, currentTraceContext, exchange, () -> {
					LOGGER.info("foo");
					this.spanInFoo = tracing.currentSpan();
				});
				return ServerResponse.ok().body(Flux.just(1), Integer.class);
			});
		}

		@Bean
		WebClient webClient() {
			return WebClient.create();
		}

		@Bean
		ManualRequestSender sender(WebClient client, Tracer tracer) {
			return new ManualRequestSender(client, tracer);
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

interface SpanProvider extends Supplier<Span> {

}
