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
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.ExtraFieldPropagation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Marcin Grzejszczak
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
		"spring.sleuth.baggage-keys=my-baggage",
		"spring.sleuth.propagation-keys=my-propagation",
		"spring.sleuth.local-keys=my-local",
		"spring.sleuth.log.slf4j.whitelisted-mdc-keys=my-baggage,my-propagation,my-local" })
@SpringBootConfiguration
@EnableAutoConfiguration
public class Slf4JSpanLoggerTest {

	@Autowired
	Tracer tracer;

	@Autowired
	Slf4jScopeDecorator slf4jScopeDecorator;

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
		ExtraFieldPropagation.set(this.span.context(), "my-propagation",
				"my-propagation-value");
		ExtraFieldPropagation.set(this.span.context(), "my-local", "my-local-value");
		Scope scope = this.slf4jScopeDecorator.decorateScope(this.span.context(), () -> {
		});

		assertThat(MDC.get("my-baggage")).isEqualTo("my-value");
		assertThat(MDC.get("my-propagation")).isEqualTo("my-propagation-value");
		assertThat(MDC.get("my-local")).isEqualTo("my-local-value");

		scope.close();

		assertThat(MDC.get("my-baggage")).isNullOrEmpty();
		assertThat(MDC.get("my-propagation")).isNullOrEmpty();
		assertThat(MDC.get("my-local")).isNullOrEmpty();
	}

	@Test
	public void should_remove_entries_from_mdc_for_null_span() throws Exception {
		ExtraFieldPropagation.set(this.span.context(), "my-baggage", "my-value");
		ExtraFieldPropagation.set(this.span.context(), "my-propagation",
				"my-propagation-value");
		this.slf4jScopeDecorator.decorateScope(this.span.context(), () -> {
		});

		assertThat(MDC.get("my-baggage")).isEqualTo("my-value");
		assertThat(MDC.get("my-propagation")).isEqualTo("my-propagation-value");

		Scope scope = this.slf4jScopeDecorator.decorateScope(null, () -> {
		});

		scope.close();

		assertThat(MDC.get("my-baggage")).isNullOrEmpty();
		assertThat(MDC.get("my-propagation")).isNullOrEmpty();
	}

	@Test
	public void should_remove_entries_from_mdc_for_null_span_and_mdc_fields_set_directly()
			throws Exception {
		MDC.put("my-baggage", "my-value");
		MDC.put("my-propagation", "my-propagation-value");

		this.slf4jScopeDecorator.decorateScope(this.span.context(), () -> {
		});

		assertThat(MDC.get("my-baggage")).isEqualTo("my-value");
		assertThat(MDC.get("my-propagation")).isEqualTo("my-propagation-value");

		Scope scope = this.slf4jScopeDecorator.decorateScope(null, () -> {
		});

		scope.close();

		assertThat(MDC.get("my-baggage")).isNullOrEmpty();
		assertThat(MDC.get("my-propagation")).isNullOrEmpty();
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
		MDC.put("my-propagation", "B");

		assertThat(MDC.get("my-baggage")).isEqualTo("A");
		assertThat(MDC.get("my-propagation")).isEqualTo("B");

		scope.close();

		assertThat(MDC.get("my-baggage")).isNullOrEmpty();
		assertThat(MDC.get("my-propagation")).isNullOrEmpty();
	}

	@Test
	public void should_pick_previous_mdc_entries_when_their_keys_are_whitelisted()
			throws Exception {

		MDC.put("my-baggage", "A1");
		MDC.put("my-propagation", "B1");

		Scope scope = this.slf4jScopeDecorator.decorateScope(this.span.context(), () -> {
		});

		MDC.put("my-baggage", "A2");
		MDC.put("my-propagation", "B2");

		assertThat(MDC.get("my-baggage")).isEqualTo("A2");
		assertThat(MDC.get("my-propagation")).isEqualTo("B2");

		scope.close();

		assertThat(MDC.get("my-baggage")).isEqualTo("A1");
		assertThat(MDC.get("my-propagation")).isEqualTo("B1");
	}

}
