package org.springframework.cloud.sleuth.instrument.web;

import org.awaitility.Awaitility;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.ListOfSpans;
import org.springframework.cloud.sleuth.assertions.SleuthAssertions;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class TraceWebFluxTests {

	@BeforeClass
	public static void setup() {
		Hooks.resetOnLastOperator();
		Schedulers.resetFactory();
	}

	@Ignore("Ignored until fixed in Reactor")
	@Test public void should_instrument_web_filter() throws Exception {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWebFluxTests.Config.class)
				.web(WebApplicationType.REACTIVE).properties("server.port=0", "spring.jmx.enabled=false",
						"spring.application.name=TraceWebFluxTests").run();
		ExceptionUtils.setFail(true);
		Span span = null;
		try {
			span = context.getBean(Tracer.class).createSpan("foo");
			int port = context.getBean(Environment.class).getProperty("local.server.port", Integer.class);
			ArrayListSpanAccumulator accumulator = context.getBean(ArrayListSpanAccumulator.class);

			Mono<ClientResponse> exchange = context.getBean(WebClient.class).get().uri("http://localhost:" + port + "/api/c2/10").exchange();

			Awaitility.await().untilAsserted(() -> {
				ClientResponse response = exchange.block();
				SleuthAssertions.then(response.statusCode().value()).isEqualTo(200);
				SleuthAssertions.then(ExceptionUtils.getLastException()).isNull();
				SleuthAssertions.then(new ListOfSpans(accumulator.getSpans()))
						.hasASpanWithLogEqualTo(Span.CLIENT_SEND)
						.hasASpanWithLogEqualTo(Span.SERVER_RECV)
						.hasASpanWithLogEqualTo(Span.SERVER_SEND)
						.hasASpanWithLogEqualTo(Span.CLIENT_RECV)
						.hasASpanWithTagEqualTo("mvc.controller.method", "successful")
						.hasASpanWithTagEqualTo("mvc.controller.class", "Controller2");
			});
		} finally {
			context.getBean(Tracer.class).close(span);
		}

	}

	@Configuration
	@EnableAutoConfiguration
	static class Config {

		@Bean WebClient webClient() {
			return WebClient.create();
		}

		@Bean Sampler sampler() {
			return new AlwaysSampler();
		}

		@Bean SpanReporter spanReporter() {
			return new ArrayListSpanAccumulator();
		}

		@Bean
		Controller2 controller2() {
			return new Controller2();
		}
	}

	@RestController
	@RequestMapping("/api/c2")
	static class Controller2 {
		@GetMapping("/{id}")
		public Flux<String> successful(@PathVariable Long id) {
			return Flux.just(id.toString());
		}
	}
}

