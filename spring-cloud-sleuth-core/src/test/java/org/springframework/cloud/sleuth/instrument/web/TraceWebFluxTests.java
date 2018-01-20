package org.springframework.cloud.sleuth.instrument.web;

import brave.sampler.Sampler;
import org.awaitility.Awaitility;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.sleuth.instrument.web.client.TraceWebClientAutoConfiguration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.BDDAssertions.then;

public class TraceWebFluxTests {

	@BeforeClass
	public static void setup() {
		Hooks.resetOnLastOperator();
		Schedulers.resetFactory();
	}

	@Test public void should_instrument_web_filter() throws Exception {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				TraceWebFluxTests.Config.class).web(WebApplicationType.REACTIVE)
				.properties("server.port=0", "spring.jmx.enabled=false",
						"spring.application.name=TraceWebFluxTests", "security.basic.enabled=false",
								"management.security.enabled=false").run();
		ArrayListSpanReporter accumulator = context.getBean(ArrayListSpanReporter.class);
		int port = context.getBean(Environment.class).getProperty("local.server.port", Integer.class);
		accumulator.clear();

		Mono<ClientResponse> exchange = WebClient.create().get()
				.uri("http://localhost:" + port + "/api/c2/10").exchange();
		ClientResponse response = exchange.block();

		Awaitility.await().untilAsserted(() -> {
			then(response.statusCode().value()).isEqualTo(200);
			then(accumulator.getSpans()).hasSize(1);
		});
		then(accumulator.getSpans().get(0).tags())
				.containsEntry("mvc.controller.method", "successful")
				.containsEntry("mvc.controller.class", "Controller2");
	}

	@Configuration
	@EnableAutoConfiguration(
			exclude = { TraceWebClientAutoConfiguration.class,
					ReactiveSecurityAutoConfiguration.class })
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

		@Bean Controller2 controller2() {
			return new Controller2();
		}
	}

	@RestController
	static class Controller2 {

		@GetMapping("/api/c2/{id}")
		public Flux<String> successful(@PathVariable Long id) {
			// #786
			then(MDC.get("X-B3-TraceId")).isNotEmpty();
			return Flux.just(id.toString());
		}
	}
}

