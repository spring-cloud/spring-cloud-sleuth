package org.springframework.cloud.sleuth.instrument.web.multiple;

import java.net.URI;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.ListOfSpans;
import org.springframework.cloud.sleuth.instrument.web.TraceFilter;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;

import static org.awaitility.Awaitility.await;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource(properties = "spring.application.name=multiplehopsintegrationtests")
@SpringBootTest(classes = MultipleHopsIntegrationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MultipleHopsIntegrationTests {

	@Autowired Tracer tracer;
	@Autowired TraceKeys traceKeys;
	@Autowired TraceFilter traceFilter;
	@Autowired ArrayListSpanAccumulator arrayListSpanAccumulator;
	@Autowired SpanReporter spanReporter;
	@Autowired RestTemplate restTemplate;
	@Autowired Config config;

	@Before
	public void setup() {
		this.arrayListSpanAccumulator.clear();
	}

	@Test
	public void should_prepare_spans_for_export() throws Exception {
		this.restTemplate.getForObject("http://localhost:" + this.config.port + "/greeting", String.class);

		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.arrayListSpanAccumulator.getSpans().stream().map(Span::getName)
					.collect(
					toList())).containsAll(asList("http:/greeting", "message:greetings",
													"message:words", "message:counts"));
		});
	}

	// issue #237 - baggage
	@Test
	public void should_propagate_the_baggage() throws Exception {
		//tag::baggage[]
		Span initialSpan = this.tracer.createSpan("span");
		initialSpan.setBaggageItem("foo", "bar");
		initialSpan.setBaggageItem("UPPER_CASE", "someValue");
		//end::baggage[]

		try {
			HttpHeaders headers = new HttpHeaders();
			headers.put("baggage-baz", Collections.singletonList("baz"));
			headers.put("BAGGAGE-bizarreCASE", Collections.singletonList("value"));
			RequestEntity requestEntity = new RequestEntity(headers, HttpMethod.GET,
					URI.create("http://localhost:" + this.config.port + "/greeting"));
			this.restTemplate.exchange(requestEntity, String.class);

			await().atMost(5, SECONDS).untilAsserted(() -> {
				then(new ListOfSpans(this.arrayListSpanAccumulator.getSpans()))
						.everySpanHasABaggage("foo", "bar")
						.everySpanHasABaggage("upper_case", "someValue")
						.anySpanHasABaggage("baz", "baz")
						.anySpanHasABaggage("bizarrecase", "value");
			});
		} finally {
			this.tracer.close(initialSpan);
		}
	}

	@Configuration
	@SpringBootApplication(exclude = JmxAutoConfiguration.class)
	public static class Config implements
			ApplicationListener<EmbeddedServletContainerInitializedEvent> {
		int port;

		@Override
		public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
			this.port = event.getEmbeddedServletContainer().getPort();
		}

		@Bean
		RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean ArrayListSpanAccumulator arrayListSpanAccumulator() {
			return new ArrayListSpanAccumulator();
		}

		@Bean Sampler defaultTraceSampler() {
			return new AlwaysSampler();
		}
	}
}
