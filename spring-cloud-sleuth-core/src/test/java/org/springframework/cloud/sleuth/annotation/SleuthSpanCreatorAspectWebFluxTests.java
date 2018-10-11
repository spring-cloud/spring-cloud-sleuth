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

package org.springframework.cloud.sleuth.annotation;

import java.util.List;

import brave.Tracer;
import brave.sampler.Sampler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.trace.http.HttpTrace;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.sleuth.DisableWebFluxSecurity;
import org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfigurationAccessorConfiguration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = { "spring.main.web-application-type=reactive" }, classes = {
		SleuthSpanCreatorAspectWebFluxTests.TestEndpoint.class,
		SleuthSpanCreatorAspectWebFluxTests.TestConfiguration.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class SleuthSpanCreatorAspectWebFluxTests {

	private static final Log log = LogFactory
			.getLog(SleuthSpanCreatorAspectWebFluxTests.class);

	@Autowired
	Tracer tracer;

	@Autowired
	AccessLoggingHttpTraceRepository repository;

	@Autowired
	ArrayListSpanReporter reporter;

	private WebTestClient webClient;

	@LocalServerPort
	private int port;

	@AfterClass
	@BeforeClass
	public static void cleanup() {
		Hooks.resetOnLastOperator();
		TraceReactorAutoConfigurationAccessorConfiguration.close();
	}

	private static String toHexString(Long value) {
		BDDAssertions.then(value).isNotNull();
		return StringUtils.leftPad(Long.toHexString(value), 16, '0');
	}

	@Before
	public void setup() {
		this.reporter.clear();
		this.repository.clear();
		log.info("Running app on port [" + this.port + "]");
		this.webClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + this.port)
				.build();
	}

	@Test
	public void shouldReturnSpanFromWebFluxTraceContext() {
		Mono<Object> mono = webClient.get().uri("/test/ping").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).kind()).isEqualTo(Span.Kind.SERVER);
			then(spans.get(0).name()).isEqualTo("get /test/ping");
			then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	private List<zipkin2.Span> getSpans() {
		List<zipkin2.Span> spans = this.reporter.getSpans();
		log.info("Reported the following spans: \n\n" + spans);
		return spans;
	}

	@Test
	public void shouldReturnSpanFromWebFluxSubscriptionContext() {
		Mono<Object> mono = webClient.get().uri("/test/pingFromContext").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).kind()).isEqualTo(Span.Kind.SERVER);
			then(spans.get(0).name()).isEqualTo("get /test/pingfromcontext");
			then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldContinueSpanInWebFlux() {
		Mono<Object> mono = webClient.get().uri("/test/continueSpan").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).kind()).isEqualTo(Span.Kind.SERVER);
			then(spans.get(0).name()).isEqualTo("get /test/continuespan");
			then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateNewSpanInWebFlux() {
		Mono<Object> mono = webClient.get().uri("/test/newSpan1").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = getSpans();
			then(spans).hasSize(2);
			then(spans.get(0).name()).isEqualTo("new-span-in-trace-context");
			then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
			then(spans.get(1).kind()).isEqualTo(Span.Kind.SERVER);
			then(spans.get(1).name()).isEqualTo("get /test/newspan1");
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldCreateNewSpanInWebFluxInSubscriberContext() {
		Mono<Object> mono = webClient.get().uri("/test/newSpan2").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = getSpans();
			then(spans).hasSize(2);
			then(spans.get(0).name()).isEqualTo("new-span-in-subscriber-context");
			then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
			then(spans.get(1).kind()).isEqualTo(Span.Kind.SERVER);
			then(spans.get(1).name()).isEqualTo("get /test/newspan2");
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Test
	public void shouldSetupCorrectSpanInHttpTrace() {
		repository.clear();

		Mono<Object> mono = webClient.get().uri("/test/ping").exchange()
				.returnResult(Object.class).getResponseBody().single();

		Object object = mono.block();
		log.info("Received [" + object + "]");
		Long newSpanId = (Long) object;

		Awaitility.await().untilAsserted(() -> {
			List<zipkin2.Span> spans = getSpans();
			then(spans).hasSize(1);
			then(spans.get(0).kind()).isEqualTo(Span.Kind.SERVER);
			then(spans.get(0).name()).isEqualTo("get /test/ping");
			then(this.repository.getSpan()).isNotNull();
			then(spans.get(0).id()).isEqualTo(toHexString(newSpanId))
					.isEqualTo(repository.getSpan().context().traceIdString());
			then(this.tracer.currentSpan()).isNull();
		});
	}

	@Configuration
	@EnableAutoConfiguration
	@DisableWebFluxSecurity
	protected static class TestConfiguration {

		@Bean
		TestBean testBean(Tracer tracer) {
			return new TestBean(tracer);
		}

		@Bean
		Reporter<zipkin2.Span> spanReporter() {
			return new ArrayListSpanReporter();
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		AccessLoggingHttpTraceRepository accessLoggingHttpTraceRepository() {
			return new AccessLoggingHttpTraceRepository();
		}

	}

	static class AccessLoggingHttpTraceRepository implements HttpTraceRepository {

		private static final Log log = LogFactory
				.getLog(AccessLoggingHttpTraceRepository.class);

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
			return Mono.just(tracer.currentSpan().context().spanId());
		}

		@GetMapping("/pingFromContext")
		Mono<Long> pingFromContext() {
			log.info("pingFromContext");
			return Mono.subscriberContext()
					.doOnSuccess(context -> log.info("Ping from context"))
					.flatMap(context -> Mono
							.just(tracer.currentSpan().context().spanId()));
		}

		@GetMapping("/continueSpan")
		Mono<Long> continueSpan() {
			log.info("continueSpan");
			return testBean.continueSpanInTraceContext();
		}

		@GetMapping("/newSpan1")
		Mono<Long> newSpan1() {
			log.info("newSpan1");
			return testBean.newSpanInTraceContext();
		}

		@GetMapping("/newSpan2")
		Mono<Long> newSpan2() {
			log.info("newSpan2");
			return testBean.newSpanInSubscriberContext();
		}

	}

	static class TestBean {

		private static final Logger log = LoggerFactory.getLogger(TestBean.class);

		private final Tracer tracer;

		TestBean(Tracer tracer) {
			this.tracer = tracer;
		}

		@ContinueSpan
		public Mono<Long> continueSpanInTraceContext() {
			log.info("Continue");
			Long span = tracer.currentSpan().context().spanId();
			return Mono.defer(() -> Mono.just(span));
		}

		@NewSpan(name = "newSpanInTraceContext")
		public Mono<Long> newSpanInTraceContext() {
			log.info("New Span in Trace Context");
			Long span = tracer.currentSpan().context().spanId();
			return Mono.defer(() -> Mono.just(span));
		}

		@NewSpan(name = "newSpanInSubscriberContext")
		public Mono<Long> newSpanInSubscriberContext() {
			log.info("New Span in Subscriber Context");
			Long span = tracer.currentSpan().context().spanId();
			return Mono.subscriberContext()
					.doOnSuccess(
							context -> log.info("New Span in deferred Trace Context"))
					.flatMap(context -> Mono.defer(() -> Mono.just(span)));
		}

	}

}
