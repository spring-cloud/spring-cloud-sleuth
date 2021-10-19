/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.prometheus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.prometheus.prometheus.SleuthSpanContextSupplier;

import static org.assertj.core.api.BDDAssertions.assertThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.when;

/**
 * Tests for {@link SleuthSpanContextSupplier}.
 *
 * @author Jonatan Ivanov
 */
@ExtendWith(MockitoExtension.class)
class SleuthSpanContextSupplierTests {

	@Mock
	private Tracer tracer;

	@InjectMocks
	private SleuthSpanContextSupplier spanContextSupplier;

	@Test
	void should_provide_null_values_if_no_span_is_available() {
		when(tracer.currentSpan()).thenReturn(null);

		assertThat(spanContextSupplier.getTraceId()).isNull();
		assertThat(spanContextSupplier.getSpanId()).isNull();
	}

	@Test
	void should_provide_values_from_tracer_if_span_is_available() {
		Span span = mock(Span.class);
		TraceContext context = mock(TraceContext.class);
		when(tracer.currentSpan()).thenReturn(span);
		when(span.context()).thenReturn(context);
		when(context.traceId()).thenReturn("42");
		when(context.spanId()).thenReturn("24");

		assertThat(spanContextSupplier.getTraceId()).isEqualTo("42");
		assertThat(spanContextSupplier.getSpanId()).isEqualTo("24");
	}

}
