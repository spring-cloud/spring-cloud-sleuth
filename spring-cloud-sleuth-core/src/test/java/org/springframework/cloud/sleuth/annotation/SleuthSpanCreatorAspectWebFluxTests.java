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
import java.util.concurrent.ConcurrentLinkedQueue;

import brave.Tracer;
import brave.sampler.Sampler;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.trace.http.HttpTrace;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.sleuth.DisableWebFluxSecurity;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(
		properties = {"spring.main.web-application-type=reactive"},
		classes = {
		SleuthSpanCreatorAspectWebFluxTests.TestEndpoint.class,
		SleuthSpanCreatorAspectWebFluxTests.TestConfiguration.class}
		, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SleuthSpanCreatorAspectWebFluxTests {

	@Autowired Tracer tracer;
	@Autowired ArrayListSpanReporter reporter;

	@Before
	public void setup() {
		this.reporter.clear();
	}

	@LocalServerPort
	private int port;

	private final WebClient webClient = WebClient.create();

	private static final ConcurrentLinkedQueue<Long> spanIdsInHttpTrace = new ConcurrentLinkedQueue<>();
	
	@Test
	public void shouldReturnSpanFromWebFluxTraceContext() {

		Mono<Long> mono = webClient.get().uri("http://localhost:"+port+"/test/ping")
				.retrieve().bodyToMono(Long.class);

		then(this.reporter.getSpans()).isEmpty();

		Long newSpanId = mono.block();

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).kind()).isEqualTo(Span.Kind.SERVER);
		then(spans.get(0).name()).isEqualTo("get /test/ping");
		then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldReturnSpanFromWebFluxSubscriptionContext() {

		Mono<Long> mono = webClient.get().uri("http://localhost:"+port+"/test/pingFromContext")
				.retrieve().bodyToMono(Long.class);

		then(this.reporter.getSpans()).isEmpty();

		Long newSpanId = mono.block();

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).kind()).isEqualTo(Span.Kind.SERVER);
		then(spans.get(0).name()).isEqualTo("get /test/pingfromcontext");
		then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldContinueSpanInWebFlux() {

		Mono<Long> mono = webClient.get().uri("http://localhost:"+port+"/test/continueSpan")
				.retrieve().bodyToMono(Long.class);

		then(this.reporter.getSpans()).isEmpty();

		Long newSpanId = mono.block();

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).kind()).isEqualTo(Span.Kind.SERVER);
		then(spans.get(0).name()).isEqualTo("get /test/continuespan");
		then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldCreateNewSpanInWebFlux() {

		Mono<Long> mono = webClient.get().uri("http://localhost:"+port+"/test/newSpan1")
				.retrieve().bodyToMono(Long.class);

		then(this.reporter.getSpans()).isEmpty();

		Long newSpanId = mono.block();

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(2);
		then(spans.get(0).name()).isEqualTo("new-span-in-trace-context");
		then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
		then(spans.get(1).kind()).isEqualTo(Span.Kind.SERVER);
		then(spans.get(1).name()).isEqualTo("get /test/newspan1");
		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldCreateNewSpanInWebFluxInSubscriberContext() {

		Mono<Long> mono = webClient.get().uri("http://localhost:"+port+"/test/newSpan2")
				.retrieve().bodyToMono(Long.class);

		then(this.reporter.getSpans()).isEmpty();

		Long newSpanId = mono.block();

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(2);
		then(spans.get(0).name()).isEqualTo("new-span-in-subscriber-context");
		then(spans.get(0).id()).isEqualTo(toHexString(newSpanId));
		then(spans.get(1).kind()).isEqualTo(Span.Kind.SERVER);
		then(spans.get(1).name()).isEqualTo("get /test/newspan2");
		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldSetupCorrectSpanInHttpTrace() {

		spanIdsInHttpTrace.clear();

		Mono<Long> mono = webClient.get().uri("http://localhost:"+port+"/test/ping")
				.retrieve().bodyToMono(Long.class);

		then(this.reporter.getSpans()).isEmpty();

		Long newSpanId = mono.block();

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).kind()).isEqualTo(Span.Kind.SERVER);
		then(spans.get(0).name()).isEqualTo("get /test/ping");
		then(spans.get(0).id())
				.isEqualTo(toHexString(newSpanId))
				.isEqualTo(toHexString(spanIdsInHttpTrace.poll()));
		then(this.tracer.currentSpan()).isNull();
	}


	private static String toHexString(long value){
		return StringUtils.leftPad(Long.toHexString(value), 16, '0');
	}

	
	@Configuration
	@EnableAutoConfiguration
	@DisableWebFluxSecurity
	protected static class TestConfiguration {

		@Bean
		public TestBean testBean(Tracer tracer) {
			return new TestBean(tracer);
		}

		@Bean Reporter<zipkin2.Span> spanReporter() {
			return new ArrayListSpanReporter();
		}

		@Bean Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean AccessLoggingHttpTraceRepository accessLoggingHttpTraceRepository(){
			return new AccessLoggingHttpTraceRepository();
		}
	}

	static class AccessLoggingHttpTraceRepository implements HttpTraceRepository {

		@Autowired Tracer tracer;

		@Override
		public List<HttpTrace> findAll() {
			return null;
		}

		@Override
		public void add(HttpTrace trace) {
			spanIdsInHttpTrace.add(tracer.currentSpan().context().spanId());
		}
	}

	@RestController
	@RequestMapping("/test")
	static class TestEndpoint {

		@Autowired Tracer tracer;
		@Autowired TestBean testBean;

		@GetMapping("/ping")
		public Mono<Long> ping() {
			return Mono.just(tracer.currentSpan().context().spanId());
		}

		@GetMapping("/pingFromContext")
		public Mono<Long> pingFromContext() {
			return Mono.subscriberContext()
					.flatMap(context -> Mono.just(tracer.currentSpan().context().spanId()));
		}

		@GetMapping("/continueSpan")
		public Mono<Long> continueSpan() {
			return testBean.continueSpanInTraceContext();
		}

		@GetMapping("/newSpan1")
		public Mono<Long> newSpan1() {
			return testBean.newSpanInTraceContext();
		}

		@GetMapping("/newSpan2")
		public Mono<Long> newSpan2() {
			return testBean.newSpanInSubscriberContext();
		}
	}

	static class TestBean {

		private final Tracer tracer;

		public TestBean(Tracer tracer) {
			this.tracer = tracer;
		}

		@ContinueSpan
		public Mono<Long> continueSpanInTraceContext() {
			return Mono.defer(() -> Mono.just(tracer.currentSpan().context().spanId()));
		}

		@NewSpan(name = "newSpanInTraceContext")
		public Mono<Long> newSpanInTraceContext() {
			return Mono.defer(() -> Mono.just(tracer.currentSpan().context().spanId()));
		}

		@NewSpan(name = "newSpanInSubscriberContext")
		public Mono<Long> newSpanInSubscriberContext() {
			return Mono.subscriberContext()
					.flatMap(context -> Mono.defer(() -> Mono.just(tracer.currentSpan().context().spanId())));
		}
	}
}
