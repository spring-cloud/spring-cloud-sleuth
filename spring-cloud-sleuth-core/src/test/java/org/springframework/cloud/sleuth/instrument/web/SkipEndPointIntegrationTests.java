package org.springframework.cloud.sleuth.instrument.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.sleuth.DisableSecurity;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import brave.Tracer;
import brave.sampler.Sampler;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SkipEndPointIntegrationTests.Config.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"management.endpoints.web.exposure.include:*",
		"server.servlet.context-path:/context-path",
		"spring.sleuth.http.legacy.enabled:true",
		"management.endpoints.web.base-path:/"})
public class SkipEndPointIntegrationTests {

	@Autowired
	private ArrayListSpanReporter accumulator;

	@Autowired
	private Tracer tracer;

	@LocalServerPort
	int port;

	@Test
	public void should_sample_non_actuator_endpoint_with_context_path() {
		new RestTemplate().getForObject(
				"http://localhost:" + this.port + "/context-path/something",
				String.class);

		then(this.tracer.currentSpan()).isNull();
		then(this.accumulator.getSpans()).hasSize(1);
	}

	@Test
	public void should_sample_actuator_endpoint_with_base_path_set_to_root() {
		new RestTemplate().getForObject(
				"http://localhost:" + this.port + "/context-path/health",
				String.class);

		then(this.tracer.currentSpan()).isNull();
		then(this.accumulator.getSpans()).hasSize(0);
	}

	@EnableAutoConfiguration(exclude = RabbitAutoConfiguration.class)
	@Configuration
	@DisableSecurity
	@RestController
	public static class Config {

		@GetMapping("something")
		void doNothing() {
		}

		@Bean
		ArrayListSpanReporter reporter() {
			return new ArrayListSpanReporter();
		}

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}
}
