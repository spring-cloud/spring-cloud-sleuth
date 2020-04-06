/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.log;

import brave.Span;
import brave.Tracer;
import brave.baggage.BaggageField;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.ExtraFieldPropagation;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
		"spring.sleuth.baggage-keys=my-baggage,my-baggage-two",
		"spring.sleuth.remote-keys=country-code", "spring.sleuth.local-keys=bp",
		"spring.sleuth.log.slf4j.whitelisted-mdc-keys=my-baggage,country-code,bp" })
@SpringBootConfiguration
@EnableAutoConfiguration
public class Slf4JSpanLoggerTest {

	static final BaggageField COUNTRY_CODE = BaggageField.create("country-code");
	static final BaggageField BUSINESS_PROCESS = BaggageField.create("bp");

	@Autowired
	Tracer tracer;

	@Autowired
	ScopeDecorator slf4jScopeDecorator;

	Span span;

	@BeforeEach
	@AfterEach
	public void setup() {
		MDC.clear();
		this.span = this.tracer.nextSpan().name("span").start();
	}

	@Test
	public void should_set_entries_to_mdc_from_span() throws Exception {
		Scope scope = this.slf4jScopeDecorator.decorateScope(this.span.context(), () -> {
		});

		assertThat(MDC.get("traceId")).isEqualTo(this.span.context().traceIdString());

		scope.close();

		assertThat(MDC.get("traceId")).isNullOrEmpty();
	}

	@Test
	public void should_set_entries_to_mdc_from_span_with_baggage() throws Exception {
		ExtraFieldPropagation.set(this.span.context(), "my-baggage", "my-value");
		COUNTRY_CODE.updateValue(this.span.context(), "FO");
		BUSINESS_PROCESS.updateValue(this.span.context(), "ALM");
		Scope scope = this.slf4jScopeDecorator.decorateScope(this.span.context(), () -> {
		});

		assertThat(MDC.get("my-baggage")).isEqualTo("my-value");
		assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("FO");
		assertThat(MDC.get(BUSINESS_PROCESS.name())).isEqualTo("ALM");

		scope.close();

		assertThat(MDC.get("my-baggage")).isNullOrEmpty();
		assertThat(MDC.get(COUNTRY_CODE.name())).isNull();
		assertThat(MDC.get(BUSINESS_PROCESS.name())).isNull();
	}

	@Test
	public void should_remove_entries_from_mdc_for_null_span() throws Exception {
		ExtraFieldPropagation.set(this.span.context(), "my-baggage", "my-value");
		COUNTRY_CODE.updateValue(this.span.context(), "FO");

		try (Scope scope1 = this.slf4jScopeDecorator.decorateScope(this.span.context(),
				NOOP)) {
			assertThat(MDC.get("my-baggage")).isEqualTo("my-value");
			assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("FO");

			try (Scope scope2 = this.slf4jScopeDecorator.decorateScope(null, NOOP)) {
				assertThat(MDC.get("my-baggage")).isNullOrEmpty();
				assertThat(MDC.get(COUNTRY_CODE.name())).isNullOrEmpty();
			}
		}
	}

	@Test
	public void should_remove_entries_from_mdc_for_null_span_and_mdc_fields_set_directly()
			throws Exception {
		MDC.put("my-baggage", "my-value");
		MDC.put(COUNTRY_CODE.name(), "FO");

		// the span is holding no baggage so it clears the preceding values
		try (Scope scope = this.slf4jScopeDecorator.decorateScope(this.span.context(),
				NOOP)) {
			assertThat(MDC.get("my-baggage")).isNullOrEmpty();
			assertThat(MDC.get(COUNTRY_CODE.name())).isNullOrEmpty();
		}

		assertThat(MDC.get("my-baggage")).isEqualTo("my-value");
		assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("FO");

		try (Scope scope = this.slf4jScopeDecorator.decorateScope(null, NOOP)) {
			assertThat(MDC.get("my-baggage")).isNullOrEmpty();
			assertThat(MDC.get(COUNTRY_CODE.name())).isNullOrEmpty();
		}

		assertThat(MDC.get("my-baggage")).isEqualTo("my-value");
		assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("FO");
	}

	@Test
	public void should_remove_entries_from_mdc_from_null_span() throws Exception {
		MDC.put("traceId", "A");

		Scope scope = this.slf4jScopeDecorator.decorateScope(null, () -> {
		});

		assertThat(MDC.get("traceId")).isNullOrEmpty();

		scope.close();

		assertThat(MDC.get("traceId")).isEqualTo("A");
	}

	// #1416
	@Test
	public void should_clear_any_mdc_entries_when_their_keys_are_whitelisted()
			throws Exception {

		Scope scope = this.slf4jScopeDecorator.decorateScope(this.span.context(), () -> {
		});

		MDC.put("my-baggage", "A");
		MDC.put(COUNTRY_CODE.name(), "FO");

		assertThat(MDC.get("my-baggage")).isEqualTo("A");
		assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("FO");

		scope.close();

		assertThat(MDC.get("my-baggage")).isNullOrEmpty();
		assertThat(MDC.get(COUNTRY_CODE.name())).isNullOrEmpty();
	}

	@Test
	public void should_only_include_whitelist() {
		assertThat(this.slf4jScopeDecorator).extracting("fields")
				.asInstanceOf(
						InstanceOfAssertFactories.array(SingleCorrelationField[].class))
				.extracting(SingleCorrelationField::name).containsOnly("traceId",
						"parentId", "spanId", "spanExportable", "my-baggage", "bp",
						COUNTRY_CODE.name()); // my-baggage-two is not in the whitelist
	}

	@Test
	public void should_pick_previous_mdc_entries_when_their_keys_are_whitelisted() {

		MDC.put("my-baggage", "A1");
		MDC.put(COUNTRY_CODE.name(), "FO");

		Scope scope = this.slf4jScopeDecorator.decorateScope(this.span.context(), () -> {
		});

		MDC.put("my-baggage", "A2");
		MDC.put(COUNTRY_CODE.name(), "BV");

		assertThat(MDC.get("my-baggage")).isEqualTo("A2");
		assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("BV");

		scope.close();

		assertThat(MDC.get("my-baggage")).isEqualTo("A1");
		assertThat(MDC.get(COUNTRY_CODE.name())).isEqualTo("FO");
	}

}
