/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.circuitbreaker;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.sleuth.ScopedSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = ReactiveCircuitBreakerIntegrationTests.TestConfig.class)
public abstract class ReactiveCircuitBreakerIntegrationTests {

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracer tracer;

	@Autowired
	ReactiveCircuitBreakerFactory factory;

	@Autowired
	CircuitService circuitService;

	@BeforeEach
	public void setup() {
		this.spans.clear();
	}

	@Test
	public void should_pass_tracing_information_when_using_circuit_breaker() {
		// given
		Tracer tracer = this.tracer;
		ScopedSpan scopedSpan = null;
		try {
			scopedSpan = tracer.startScopedSpan("start");
			// when
			Span span = this.factory.create("name").run(Mono.defer(() -> Mono.just(tracer.currentSpan()))).block();

			BDDAssertions.then(span).isNotNull();
			BDDAssertions.then(scopedSpan.context().traceId()).isEqualTo(span.context().traceId());
		}
		finally {
			scopedSpan.end();
		}
	}

	@Test
	public void should_pass_tracing_information_when_using_circuit_breaker_with_fallback() {
		// when
		BDDAssertions.then(this.circuitService.call().block()).isEqualTo("fallback");

		BDDAssertions.then(this.spans).hasSize(2);
		String traceId = this.circuitService.firstSpan.context().traceId();
		BDDAssertions.then(this.circuitService.secondSpan.context().traceId()).isEqualTo(traceId);

		FinishedSpan finishedSpan = this.spans.get(0);
		BDDAssertions.then(finishedSpan.getName()).contains("CircuitBreakerIntegrationTests");

		finishedSpan = this.spans.get(1);
		BDDAssertions.then(finishedSpan.getName()).contains("function");
	}

	public void assertException(FinishedSpan finishedSpan) {
		throw new UnsupportedOperationException("Implement this assertion");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	public static class TestConfig {

		@Bean
		ReactiveResilience4JCircuitBreakerFactory reactiveResilience4JCircuitBreakerFactory() {
			return new ReactiveResilience4JCircuitBreakerFactory();
		}

		@Bean
		CircuitService circuitService(ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory, Tracer tracer) {
			return new CircuitService(reactiveCircuitBreakerFactory, tracer);
		}

	}

	static class CircuitService {

		private static final Logger log = LoggerFactory.getLogger(CircuitService.class);

		private final ReactiveCircuitBreakerFactory factory;

		private final Tracer tracer;

		Span firstSpan;

		Span secondSpan;

		CircuitService(ReactiveCircuitBreakerFactory factory, Tracer tracer) {
			this.factory = factory;
			this.tracer = tracer;
		}

		Mono<String> call() {
			return this.factory.create("circuit").run(Mono.defer(() -> {
				this.firstSpan = this.tracer.currentSpan();
				log.info("<ACCEPTANCE_TEST> <TRACE:{}> Hello from consumer",
						this.tracer.currentSpan().context().traceId());
				return Mono.error(new IllegalStateException("boom"));
			}), throwable -> {
				this.secondSpan = this.tracer.currentSpan();
				log.info("<ACCEPTANCE_TEST> <TRACE:{}> Hello from producer",
						this.tracer.currentSpan().context().traceId());
				return Mono.just("fallback");
			});
		}

	}

}
