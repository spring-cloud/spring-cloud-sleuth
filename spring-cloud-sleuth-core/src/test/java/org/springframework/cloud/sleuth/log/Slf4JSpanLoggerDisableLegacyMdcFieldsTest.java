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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Bjarte Stien Karlsen
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.sleuth.log.slf4j.disable-legacy-keys=true" })
@EnableAutoConfiguration
public class Slf4JSpanLoggerDisableLegacyMdcFieldsTest {

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
	public void should_set_entries_to_mdc_from_span_with_no_legacy_keys()
			throws Exception {
		Scope scope = this.slf4jScopeDecorator.decorateScope(this.span.context(), () -> {
		});

		assertThat(MDC.get("X-B3-TraceId")).isNullOrEmpty();
		assertThat(MDC.get("traceId")).isEqualTo(this.span.context().traceIdString());

		scope.close();

		assertThat(MDC.get("traceId")).isNullOrEmpty();
	}

}
