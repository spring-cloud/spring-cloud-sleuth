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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

import brave.Span;
import brave.Tracer;
import brave.sampler.Sampler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.trace.http.HttpTrace;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.sleuth.DisableWebFluxSecurity;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfigurationAccessorConfiguration;
import org.springframework.cloud.sleuth.instrument.web.client.TraceWebClientAutoConfiguration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.BDDAssertions.then;

@NotThreadSafe
public class TraceWebFluxTests {

	public static final String EXPECTED_TRACE_ID = "b919095138aa4c6e";

	@Test
	public void should_instrument_web_filter() throws Exception {
		// setup
		TraceReactorAutoConfigurationAccessorConfiguration.close();
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				TraceWebFluxTests.Config.class)
						.web(WebApplicationType.REACTIVE)
						.properties("server.port=0", "spring.jmx.enabled=false",
								"spring.sleuth.web.skipPattern=/skipped",
								"spring.application.name=TraceWebFluxTests",
								"security.basic.enabled=false",
								"management.security.enabled=false")
						.run();
		ArrayListSpanReporter accumulator = context.getBean(ArrayListSpanReporter.class);
		int port = context.getBean(Environment.class).getProperty("local.server.port",
				Integer.class);
		Controller2 controller2 = context.getBean(Controller2.class);
		clean(accumulator, controller2);

		// when
		ClientResponse response = whenRequestIsSent(port);
		// then
		thenSpanWasReportedWithTags(accumulator, response);
		clean(accumulator, controller2);

		// when
		ClientResponse functionResponse = whenRequestIsSentToFunction(port);
		// then
		thenSpanWasReportedForFunction(accumulator, functionResponse);
		accumulator.clear();

		// when
		ClientResponse nonSampledResponse = whenNonSampledRequestIsSent(port);
		// then
		thenNoSpanWasReported(accumulator, nonSampledResponse, controller2);
		accumulator.clear();

		// when
		ClientResponse skippedPatternResponse = whenRequestIsSentToSkippedPattern(port);
		// then
		thenNoSpanWasReported(accumulator, skippedPatternResponse, controller2);

		// some other tests
		SleuthSpanCreatorAspectWebFlux bean = context
				.getBean(SleuthSpanCreatorAspectWebFlux.class);
		bean.setPort(port);

		// then
		bean.shouldContinueSpanInWebFlux();
		bean.shouldCreateNewSpanInWebFlux();
		bean.shouldCreateNewSpanInWebFluxInSubscriberContext();
		bean.shouldReturnSpanFromWebFluxSubscriptionContext();
		bean.shouldReturnSpanFromWebFluxTraceContext();
		bean.shouldSetupCorrectSpanInHttpTrace();

		// cleanup
		context.close();
		TraceReactorAutoConfigurationAccessorConfiguration.close();
	}

	private void clean(ArrayListSpanReporter accumulator, Controller2 controller2) {
		accumulator.clear();
		controller2.span = null;
	}

	private void thenSpanWasReportedWithTags(ArrayListSpanReporter accumulator,
			ClientResponse response) {
		Awaitility.await().untilAsserted(() -> {
			then(response.statusCode().value()).isEqualTo(200);
		});
		List<zipkin2.Span> spans = accumulator.getSpans().stream()
				.filter(span -> span.name().equals("get /api/c2/{id}"))
				.collect(Collectors.toList());
		then(spans).hasSize(1);
		then(spans.get(0).name()).isEqualTo("get /api/c2/{id}");
		then(spans.get(0).tags()).containsEntry("mvc.controller.method", "successful")
				.containsEntry("mvc.controller.class", "Controller2");
	}

	private void thenSpanWasReportedForFunction(ArrayListSpanReporter accumulator,
			ClientResponse response) {
		Awaitility.await().untilAsserted(() -> {
			then(response.statusCode().value()).isEqualTo(200);
		});
		List<zipkin2.Span> spans = accumulator.getSpans().stream()
				.filter(span -> span.name().equals("get")).collect(Collectors.toList());
		then(spans).hasSize(1);
	}

	private void thenNoSpanWasReported(ArrayListSpanReporter accumulator,
			ClientResponse response, Controller2 controller2) {
		Awaitility.await().untilAsserted(() -> {
			then(response.statusCode().value()).isEqualTo(200);
			then(accumulator.getSpans()).isEmpty();
		});
		then(controller2.span).isNotNull();
		then(controller2.span.context().traceIdString()).isEqualTo(EXPECTED_TRACE_ID);
	}

	private ClientResponse whenRequestIsSent(int port) {
		Mono<ClientResponse> exchange = WebClient.create().get()
				.uri("http://localhost:" + port + "/api/c2/10").exchange();
		return exchange.block();
	}

	private ClientResponse whenRequestIsSentToFunction(int port) {
		Mono<ClientResponse> exchange = WebClient.create().get()
				.uri("http://localhost:" + port + "/function").exchange();
		return exchange.block();
	}

	private ClientResponse whenRequestIsSentToSkippedPattern(int port) {
		Mono<ClientResponse> exchange = WebClient.create().get()
				.uri("http://localhost:" + port + "/skipped").exchange();
		return exchange.block();
	}

	private ClientResponse whenNonSampledRequestIsSent(int port) {
		Mono<ClientResponse> exchange = WebClient.create().get()
				.uri("http://localhost:" + port + "/api/c2/10")
				.header("X-B3-SpanId", EXPECTED_TRACE_ID)
				.header("X-B3-TraceId", EXPECTED_TRACE_ID).header("X-B3-Sampled", "0")
				.exchange();
		return exchange.block();
	}

	@Configuration
	@EnableAutoConfiguration(exclude = { TraceWebClientAutoConfiguration.class })
	@DisableWebFluxSecurity
	static class Config {

		@Bean
		WebClient webClient() {
			return WebClient.create();
		}

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		ArrayListSpanReporter spanReporter() {
			return new ArrayListSpanReporter();
		}

		@Bean
		Controller2 controller2(Tracer tracer) {
			return new Controller2(tracer);
		}

		@Bean
		TestEndpoint testEndpoint() {
			return new TestEndpoint();
		}

		@Bean
		RouterFunction<ServerResponse> function() {
			return RouterFunctions.route(RequestPredicates.GET("/function"), r -> {
				then(MDC.get("X-B3-TraceId")).isNotEmpty();
				return ServerResponse.ok().syncBody("functionOk");
			});
		}

		@Bean
		TestBean testBean(Tracer tracer) {
			return new TestBean(tracer);
		}

		@Bean
		SleuthSpanCreatorAspectWebFlux.AccessLoggingHttpTraceRepository accessLoggingHttpTraceRepository() {
			return new SleuthSpanCreatorAspectWebFlux.AccessLoggingHttpTraceRepository();
		}

		@Bean
		SleuthSpanCreatorAspectWebFlux sleuthSpanCreatorAspectWebFlux(Tracer tracer,
				SleuthSpanCreatorAspectWebFlux.AccessLoggingHttpTraceRepository repository,
				ArrayListSpanReporter reporter) {
			return new SleuthSpanCreatorAspectWebFlux(tracer, repository, reporter);
		}

	}

	@RestController
	static class Controller2 {

		private final Tracer tracer;

		Span span;

		Controller2(Tracer tracer) {
			this.tracer = tracer;
		}

		@GetMapping("/api/c2/{id}")
		public Flux<String> successful(@PathVariable Long id) {
			// #786
			then(MDC.get("X-B3-TraceId")).isNotEmpty();
			this.span = this.tracer.currentSpan();
			return Flux.just(id.toString());
		}

		@GetMapping("/skipped")
		public Flux<String> skipped() {
			Boolean sampled = this.tracer.currentSpan().context().sampled();
			then(sampled).isFalse();
			return Flux.just(sampled.toString());
		}

	}

	@RestController
	@RequestMapping("/test")
	static class TestEndpoint {

		private static final Logger log = LoggerFactory.getLogger(TestEndpoint.class);

		@Autowired
		Tracer tracer;

		@Autowired
		TestBean testBean;

		@GetMapping("/ping")
		Mono<Long> ping() {
			log.info("ping");
			return Mono.just(this.tracer.currentSpan().context().spanId());
		}

		@GetMapping("/pingFromContext")
		Mono<Long> pingFromContext() {
			log.info("pingFromContext");
			return Mono.subscriberContext()
					.doOnSuccess(context -> log.info("Ping from context"))
					.flatMap(context -> Mono
							.just(this.tracer.currentSpan().context().spanId()));
		}

		@GetMapping("/continueSpan")
		Mono<Long> continueSpan() {
			log.info("continueSpan");
			return this.testBean.continueSpanInTraceContext();
		}

		@GetMapping("/newSpan1")
		Mono<Long> newSpan1() {
			log.info("newSpan1");
			return this.testBean.newSpanInTraceContext();
		}

		@GetMapping("/newSpan2")
		Mono<Long> newSpan2() {
			log.info("newSpan2");
			return this.testBean.newSpanInSubscriberContext();
		}

	}

}

class SleuthSpanCreatorAspectWebFlux {

	private static final Log log = LogFactory
			.getLog(SleuthSpanCreatorAspectWebFlux.class);

	private final Tracer tracer;

	private final SleuthSpanCreatorAspectWebFlux.AccessLoggingHttpTraceRepository repository;

	private final ArrayListSpanReporter reporter;

	int port;

	private WebTestClient webClient;

	SleuthSpanCreatorAspectWebFlux(Tracer tracer,
			AccessLoggingHttpTraceRepository repository, ArrayListSpanReporter reporter) {
		this.tracer = tracer;
		this.repository = repository;
		this.reporter = reporter;
	}

	private static String toHexString(Long value) {
		BDDAssertions.then(value).isNotNull();
		return StringUtils.leftPad(Long.toHexString(value), 16, '0');
	}

	void setPort(int port) {
		this.port = port;
	}

	public void setup() {
		this.reporter.clear();
		this.repository.clear();
		log.info("Running app on port [" + this.port + "]");
		this.webClient = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + this.port).build();
	}

	public void shouldReturnSpanFromWebFluxTraceContext() {
		setup();
		Mono<Object> mono = this.webClient.get().uri("/test/ping").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = getSpans();
			zipkin2.Span spanToFind = spans.stream()
					.filter(span -> span.name().equals("get /test/ping")).findFirst()
					.orElseThrow(() -> new AssertionError(
							"No span with name [get /test/ping] found"));
			then(spanToFind.kind()).isEqualTo(zipkin2.Span.Kind.SERVER);
			then(spanToFind.name()).isEqualTo("get /test/ping");
			then(spanToFind.id()).isEqualTo(toHexString(newSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	private List<zipkin2.Span> getSpans() {
		List<zipkin2.Span> spans = this.reporter.getSpans();
		log.info("Reported the following spans: \n\n" + spans);
		return spans;
	}

	public void shouldReturnSpanFromWebFluxSubscriptionContext() {
		setup();
		Mono<Object> mono = this.webClient.get().uri("/test/pingFromContext").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = getSpans();
			zipkin2.Span pingFromContext = spans.stream()
					.filter(span -> span.name().equals("get /test/pingfromcontext"))
					.findFirst().orElseThrow(() -> new AssertionError(
							"No span with name [get /test/pingfromcontext] found"));
			then(pingFromContext.name()).isEqualTo("get /test/pingfromcontext");
			then(pingFromContext.kind()).isEqualTo(zipkin2.Span.Kind.SERVER);
			then(pingFromContext.id()).isEqualTo(toHexString(newSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	public void shouldContinueSpanInWebFlux() {
		setup();
		Mono<Object> mono = this.webClient.get().uri("/test/continueSpan").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = getSpans();
			zipkin2.Span spanToFind = spans.stream().filter(span -> span.name().equals("get /test/continuespan"))
					.findFirst()
					.orElseThrow(() -> new AssertionError("No span with name [get /test/continuespan] found"));
			then(spanToFind.kind()).isEqualTo(zipkin2.Span.Kind.SERVER);
			then(spanToFind.name()).isEqualTo("get /test/continuespan");
			then(spanToFind.id()).isEqualTo(toHexString(newSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	public void shouldCreateNewSpanInWebFlux() {
		setup();
		Mono<Object> mono = this.webClient.get().uri("/test/newSpan1").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			then(spanWithName("new-span-in-trace-context").id())
					.isEqualTo(toHexString(newSpanId));
			then(spanWithName("get /test/newspan1").kind())
					.isEqualTo(zipkin2.Span.Kind.SERVER);
			then(this.tracer.currentSpan()).isNull();
		});
	}

	private zipkin2.Span spanWithName(String name) {
		return getSpans().stream().filter(span -> name.equals(span.name())).findFirst()
				.orElseThrow(() -> new AssertionError(
						"Span with name [" + name + "] not found"));
	}

	public void shouldCreateNewSpanInWebFluxInSubscriberContext() {
		setup();
		Mono<Object> mono = this.webClient.get().uri("/test/newSpan2").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			then(spanWithName("new-span-in-subscriber-context").id())
					.isEqualTo(toHexString(newSpanId));
			then(spanWithName("get /test/newspan2").kind())
					.isEqualTo(zipkin2.Span.Kind.SERVER);
			then(this.tracer.currentSpan()).isNull();
		});
	}

	public void shouldSetupCorrectSpanInHttpTrace() {
		setup();

		Mono<Object> mono = this.webClient.get().uri("/test/ping").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			then(spanWithName("get /test/ping").kind())
					.isEqualTo(zipkin2.Span.Kind.SERVER);
			then(this.repository.getSpan()).isNotNull();
			then(spanWithName("get /test/ping").id()).isEqualTo(toHexString(newSpanId))
					.isEqualTo(this.repository.getSpan().context().traceIdString());
			then(this.tracer.currentSpan()).isNull();
		});
	}

	static class AccessLoggingHttpTraceRepository implements HttpTraceRepository {

		private static final Log log = LogFactory.getLog(
				SleuthSpanCreatorAspectWebFlux.AccessLoggingHttpTraceRepository.class);

		@Autowired
		Tracer tracer;

		brave.Span span;

		@Override
		public List<HttpTrace> findAll() {
			log.info("Find all executed");
			return null;
		}

		@Override
		public void add(HttpTrace trace) {
			this.span = this.tracer.currentSpan();
			log.info("Setting span [" + this.span + "]");
		}

		public brave.Span getSpan() {
			return this.span;
		}

		public void clear() {
			this.span = null;
		}

	}

}

class TestBean {

	private static final Logger log = LoggerFactory.getLogger(TestBean.class);

	private final Tracer tracer;

	TestBean(Tracer tracer) {
		this.tracer = tracer;
	}

	@ContinueSpan
	public Mono<Long> continueSpanInTraceContext() {
		log.info("Continue");
		Long span = this.tracer.currentSpan().context().spanId();
		return Mono.defer(() -> Mono.just(span));
	}

	@NewSpan(name = "newSpanInTraceContext")
	public Mono<Long> newSpanInTraceContext() {
		log.info("New Span in Trace Context");
		return Mono.defer(() -> Mono.just(this.tracer.currentSpan().context().spanId()));
	}

	@NewSpan(name = "newSpanInSubscriberContext")
	public Mono<Long> newSpanInSubscriberContext() {
		log.info("New Span in Subscriber Context");
		return Mono.subscriberContext()
				.doOnSuccess(context -> log.info("New Span in deferred Trace Context"))
				.flatMap(context -> Mono.defer(
						() -> Mono.just(this.tracer.currentSpan().context().spanId())));
	}

}
