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

package org.springframework.cloud.sleuth.log;

import brave.Span;
import brave.Tracer;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.ExtraFieldPropagation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
			"spring.sleuth.baggage-keys=my-baggage",
			"spring.sleuth.log.slf4j.whitelisted-mdc-keys=my-baggage"
		})
@SpringBootConfiguration
@EnableAutoConfiguration
public class Slf4JSpanLoggerTest {

	@Autowired Tracer tracer;
	@Autowired Slf4jScopeDecorator slf4jScopeDecorator;

	Span span;
	
	@Before
	@After
	public void setup() {
		MDC.clear();
		this.span = this.tracer.nextSpan().name("span").start();
	}

	@Test
	public void should_set_entries_to_mdc_from_span() throws Exception {
		Scope scope = this.slf4jScopeDecorator.decorateScope(this.span.context(), () -> { });

		assertThat(MDC.get("X-B3-TraceId")).isEqualTo(this.span.context().traceIdString());
		assertThat(MDC.get("traceId")).isEqualTo(this.span.context().traceIdString());

		scope.close();

		assertThat(MDC.get("X-B3-TraceId")).isNullOrEmpty();
		assertThat(MDC.get("traceId")).isNullOrEmpty();
	}

	@Test
	public void should_set_entries_to_mdc_from_span_with_baggage() throws Exception {
		ExtraFieldPropagation.set(this.span.context(), "my-baggage", "my-value");
		Scope scope = this.slf4jScopeDecorator.decorateScope(this.span.context(), () -> { });

		assertThat(MDC.get("my-baggage")).isEqualTo("my-value");

		scope.close();

		assertThat(MDC.get("my-baggage")).isNullOrEmpty();
	}

	@Test
	public void should_remove_entries_from_mdc_from_null_span() throws Exception {
		MDC.put("X-B3-TraceId", "A");
		MDC.put("traceId", "A");

		Scope scope = this.slf4jScopeDecorator.decorateScope(null, () -> { });

		assertThat(MDC.get("X-B3-TraceId")).isNullOrEmpty();
		assertThat(MDC.get("traceId")).isNullOrEmpty();

		scope.close();

		assertThat(MDC.get("X-B3-TraceId")).isEqualTo("A");
		assertThat(MDC.get("traceId")).isEqualTo("A");
	}
}