/*
 * Copyright 2022-2022 the original author or authors.
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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link LazySleuthSpanContextSupplier}.
 *
 * @author Jonatan Ivanov
 */
class LazySleuthSpanContextSupplierTests {

	@Test
	void should_provide_null_values_if_not_initialized() {
		LazySleuthSpanContextSupplier spanContextSupplier = new LazySleuthSpanContextSupplier(null);
		assertThat(spanContextSupplier.getTraceId()).isNull();
		assertThat(spanContextSupplier.getSpanId()).isNull();
	}

	@Test
	void should_provide_null_values_if_initialized_without_delegate() {
		ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
		when(tracerProvider.getIfAvailable()).thenReturn(null);

		LazySleuthSpanContextSupplier spanContextSupplier = new LazySleuthSpanContextSupplier(tracerProvider);
		spanContextSupplier.afterSingletonsInstantiated();
		assertThat(spanContextSupplier.getTraceId()).isNull();
		assertThat(spanContextSupplier.getSpanId()).isNull();
	}

	@Test
	void should_provide_values_from_delegate_if_initialized() {
		ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
		Tracer tracer = mock(Tracer.class);
		Span span = mock(Span.class);
		TraceContext context = mock(TraceContext.class);

		when(tracerProvider.getIfAvailable()).thenReturn(tracer);
		when(tracer.currentSpan()).thenReturn(span);
		when(span.context()).thenReturn(context);
		when(context.sampled()).thenReturn(true);
		when(context.traceId()).thenReturn("42");
		when(context.spanId()).thenReturn("24");

		LazySleuthSpanContextSupplier spanContextSupplier = new LazySleuthSpanContextSupplier(tracerProvider);
		spanContextSupplier.afterSingletonsInstantiated();
		assertThat(spanContextSupplier.getTraceId()).isEqualTo("42");
		assertThat(spanContextSupplier.getSpanId()).isEqualTo("24");
	}

}
