/*
 * Copyright 2013-2020 the original author or authors.
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

import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.sleuth.api.ScopedSpan;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = CircuitBreakerIntegrationTests.TestConfig.class)
public abstract class CircuitBreakerIntegrationTests {

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracer tracer;

	@Autowired
	CircuitBreakerFactory factory;

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
			Span span = this.factory.create("name").run(tracer::currentSpan);

			BDDAssertions.then(span).isNotNull();
			BDDAssertions.then(scopedSpan.context().traceId()).isEqualTo(span.context().traceId());
		}
		finally {
			scopedSpan.end();
		}
	}

	@Test
	public void should_pass_tracing_information_when_using_circuit_breaker_with_fallback() {
		// given
		Tracer tracer = this.tracer;
		AtomicReference<Span> first = new AtomicReference<>();
		AtomicReference<Span> second = new AtomicReference<>();
		ScopedSpan scopedSpan = null;
		try {
			scopedSpan = tracer.startScopedSpan("start");
			// when
			BDDAssertions.thenThrownBy(() -> this.factory.create("name").run(() -> {
				first.set(tracer.currentSpan());
				throw new IllegalStateException("boom");
			}, throwable -> {
				second.set(tracer.currentSpan());
				throw new IllegalStateException("boom2");
			})).isInstanceOf(IllegalStateException.class).hasMessageContaining("boom2");

			BDDAssertions.then(this.spans).hasSize(2);
			BDDAssertions.then(scopedSpan.context().traceId()).isEqualTo(first.get().context().traceId());
			BDDAssertions.then(scopedSpan.context().traceId()).isEqualTo(second.get().context().traceId());
			BDDAssertions.then(first.get().context().spanId()).isNotEqualTo(second.get().context().spanId());

			FinishedSpan finishedSpan = this.spans.get(0);
			BDDAssertions.then(finishedSpan.getName()).contains("CircuitBreakerIntegrationTests");
			assertException(finishedSpan);

			finishedSpan = this.spans.get(1);
			BDDAssertions.then(finishedSpan.getName()).contains("CircuitBreakerIntegrationTests");
			assertException(finishedSpan);
		}
		finally {
			scopedSpan.end();
		}
	}

	public void assertException(FinishedSpan finishedSpan) {
		throw new UnsupportedOperationException("Implement this assertion");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	public static class TestConfig {

		@Bean
		Resilience4JCircuitBreakerFactory resilience4JCircuitBreakerFactory() {
			return new Resilience4JCircuitBreakerFactory();
		}

	}

}
