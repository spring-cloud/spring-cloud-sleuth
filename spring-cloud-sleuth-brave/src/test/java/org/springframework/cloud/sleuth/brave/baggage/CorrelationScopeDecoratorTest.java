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

package org.springframework.cloud.sleuth.brave.baggage;

import brave.Span;
import brave.Tracer;
import brave.baggage.BaggageField;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import static brave.propagation.CurrentTraceContext.Scope.NOOP;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Marcin Grzejszczak
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.sleuth.baggage.remote-fields=x-vcap-request-id,country-code",
				"spring.sleuth.baggage.local-fields=bp", "spring.sleuth.baggage.correlation-fields=country-code,bp" })
@SpringBootConfiguration
@EnableAutoConfiguration
public class CorrelationScopeDecoratorTest {

	static final BaggageField COUNTRY_CODE = BaggageField.create("country-code");
	static final BaggageField BUSINESS_PROCESS = BaggageField.create("bp");

	@Autowired
	Tracer tracer;

	@Autowired
	ScopeDecorator scopeDecorator;

	Span span;

	@BeforeEach
	@AfterEach
	public void setup() {
		MDC.clear();
		this.span = this.tracer.nextSpan().name("span").start();
	}

	@Test
	public void should_set_entries_to_mdc_from_span() {
		// can't use NOOP as it is special cased
		try (Scope scope = this.scopeDecorator.decorateScope(this.span.context(), () -> {
		})) {
			assertThat(MDC.get("traceId")).isEqualTo(this.span.context().traceIdString());
		}

		assertThat(MDC.get("traceId")).isNullOrEmpty();
	}

	@Test
	public void should_set_entries_to_mdc_from_span_with_baggage() {
		COUNTRY_CODE.updateValue(this.span.context(), "FO");
		BUSINESS_PROCESS.updateValue(this.span.context(), "ALM");

		try (Scope scope = this.scopeDecorator.decorateScope(this.span.context(), NOOP)) {
			assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("FO");
			assertThat(MDC.get(BUSINESS_PROCESS.name())).isEqualTo("ALM");
		}

		assertThat(MDC.get(COUNTRY_CODE.name())).isNull();
		assertThat(MDC.get(BUSINESS_PROCESS.name())).isNull();
	}

	@Test
	public void should_remove_entries_from_mdc_for_null_span() {
		COUNTRY_CODE.updateValue(this.span.context(), "FO");

		try (Scope scope1 = this.scopeDecorator.decorateScope(this.span.context(), NOOP)) {
			assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("FO");

			try (Scope scope2 = this.scopeDecorator.decorateScope(null, NOOP)) {
				assertThat(MDC.get(COUNTRY_CODE.name())).isNullOrEmpty();
			}
		}
	}

	@Test
	public void should_remove_entries_from_mdc_for_null_span_and_mdc_fields_set_directly() {
		MDC.put(COUNTRY_CODE.name(), "FO");

		// the span is holding no baggage so it clears the preceding values
		try (Scope scope = this.scopeDecorator.decorateScope(this.span.context(), NOOP)) {
			assertThat(MDC.get(COUNTRY_CODE.name())).isNullOrEmpty();
		}

		assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("FO");

		try (Scope scope = this.scopeDecorator.decorateScope(null, NOOP)) {
			assertThat(MDC.get(COUNTRY_CODE.name())).isNullOrEmpty();
		}

		assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("FO");
	}

	@Test
	public void should_remove_entries_from_mdc_from_null_span() {
		MDC.put("traceId", "A");

		// can't use NOOP as it is special cased
		try (Scope scope = this.scopeDecorator.decorateScope(null, () -> {
		})) {
			assertThat(MDC.get("traceId")).isNullOrEmpty();
		}

		assertThat(MDC.get("traceId")).isEqualTo("A");
	}

	@Test
	public void should_only_include_whitelist() {
		assertThat(this.scopeDecorator).extracting("fields")
				.asInstanceOf(InstanceOfAssertFactories.array(SingleCorrelationField[].class))
				.extracting(SingleCorrelationField::name).containsOnly("traceId", "spanId", "bp", COUNTRY_CODE.name());
	}

	@Test
	public void should_pick_previous_mdc_entries_when_their_keys_are_whitelisted() {
		MDC.put(COUNTRY_CODE.name(), "FO");

		try (Scope scope = this.scopeDecorator.decorateScope(this.span.context(), NOOP)) {
			MDC.put(COUNTRY_CODE.name(), "BV");

			assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("BV");
		}

		assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("FO");
	}

}
