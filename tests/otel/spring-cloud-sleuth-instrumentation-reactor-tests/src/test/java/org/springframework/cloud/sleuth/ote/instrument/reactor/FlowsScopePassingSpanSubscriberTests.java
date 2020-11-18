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

package org.springframework.cloud.sleuth.ote.instrument.reactor;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;

import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.otel.bridge.OtelAccessor;

/**
 * @author Marcin Grzejszczak
 */
public class FlowsScopePassingSpanSubscriberTests
		extends org.springframework.cloud.sleuth.instrument.reactor.FlowsScopePassingSpanSubscriberTests {

	CurrentTraceContext currentTraceContext = OtelAccessor.currentTraceContext();

	TraceContext context = OtelAccessor.traceContext(SpanContext.create(TraceId.fromLongs(1L, 0L), SpanId.fromLong(1L),
			TraceFlags.getSampled(), TraceState.builder().build()));

	@Override
	protected CurrentTraceContext currentTraceContext() {
		return this.currentTraceContext;
	}

	@Override
	protected TraceContext context() {
		return this.context;
	}

}
