package org.springframework.cloud.brave.instrument.web;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.sampler.Sampler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.brave.util.ArrayListSpanReporter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

public class TraceWebFluxTests {

	@BeforeClass
	public static void setup() {
		Hooks.resetOnLastOperator();
		Schedulers.resetFactory();
	}

	@Test public void should_instrument_web_filter() throws Exception {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				TraceWebFluxTests.Config.class)
				.web(WebApplicationType.REACTIVE).properties("server.port=0", "spring.jmx.enabled=false",
						"spring.application.name=TraceWebFluxTests").run();
		Tracing tracing = context.getBean(Tracing.class);
		Span span = tracing.tracer().nextSpan().name("foo");
		ArrayListSpanReporter accumulator = context.getBean(ArrayListSpanReporter.class);

		try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
			int port = context.getBean(Environment.class).getProperty("local.server.port", Integer.class);

			Mono<ClientResponse> exchange = context.getBean(WebClient.class).get()
					.uri("http://localhost:" + port + "/api/c2/10").exchange();

			Awaitility.await().untilAsserted(() -> {
				ClientResponse response = exchange.block();
				BDDAssertions.then(response.statusCode().value()).isEqualTo(200);
			});
		} finally {
			span.finish();
		}

		BDDAssertions.then(accumulator.getSpans()).hasSize(1);
		BDDAssertions.then(accumulator.getSpans().get(0).tags())
				.containsEntry("mvc.controller.method", "successful")
				.containsEntry("mvc.controller.class", "Controller2");
	}

	@Configuration
	@EnableAutoConfiguration(exclude = TraceWebServletAutoConfiguration.class)
	static class Config {

		@Bean WebClient webClient() {
			return WebClient.create();
		}

		@Bean Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean ArrayListSpanReporter spanReporter() {
			return new ArrayListSpanReporter();
		}

		@Bean
		Controller2 controller2() {
			return new Controller2();
		}
	}

	@RestController
	static class Controller2 {
		@GetMapping("/api/c2/{id}")
		public Flux<String> successful(@PathVariable Long id) {
			return Flux.just(id.toString());
		}
	}
}

