package org.springframework.cloud.sleuth.sample;

import java.util.Random;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * @author Spencer Gibb
 */
@Configuration
@EnableAutoConfiguration
@EnableAspectJAutoProxy
@Slf4j
public class SampleApplication {

	public static final String CLIENT_NAME = "testApp";

	@RestController
	protected static class SampleController implements
			ApplicationListener<EmbeddedServletContainerInitializedEvent> {
		@Autowired
		private RestTemplate restTemplate;
		@Autowired
		private Trace trace;
		private int port;

		@SneakyThrows
		@RequestMapping("/")
		public String hi() {
			final Random random = new Random();
			Thread.sleep(random.nextInt(1000));

			String s = restTemplate.getForObject("http://localhost:" + port + "/hi2",
					String.class);
			return "hi/" + s;
		}

		@SneakyThrows
		@RequestMapping("/hi2")
		public String hi2() {
			final Random random = new Random();
			Thread.sleep(random.nextInt(1000));
			return "hi2";
		}

		@SneakyThrows
		@RequestMapping("/traced")
		public String traced() {
			TraceScope scope = trace.startSpan("customTraceEndpoint", new AlwaysSampler());
			final Random random = new Random();
			int millis = random.nextInt(1000);
			log.info("Sleeping for {} millis", millis);
			Thread.sleep(millis);

			String s = restTemplate.getForObject("http://localhost:" + port + "/hi2", String.class);
			scope.close();
			return "hi/" + s;
		}

		@Override
		public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
			port = event.getEmbeddedServletContainer().getPort();
		}
	}

	@Bean
	public Sampler defaultSampler() {
		return new AlwaysSampler();
	}

	public static void main(String[] args) {
		SpringApplication.run(SampleApplication.class, args);
	}

	/*
	 * @Bean public SpanCollector spanCollector() { return new LoggingSpanCollectorImpl();
	 * }
	 */

}
