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

package org.springframework.cloud.sleuth.brave.otelbridge;

import brave.propagation.TraceContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.TraceState;

public class BraveSpanContext extends io.opentelemetry.trace.SpanContext {

	final TraceContext traceContext;

	public BraveSpanContext(TraceContext traceContext) {
		this.traceContext = traceContext;
	}

	@Override
	public TraceId getTraceId() {
		// TODO: double check this
		return new TraceId(this.traceContext.traceIdHigh(), this.traceContext.traceId());
	}

	@Override
	public SpanId getSpanId() {
		// TODO: double check this
		return new SpanId(this.traceContext.spanId());
	}

	@Override
	public TraceFlags getTraceFlags() {
		return TraceFlags.builder().setIsSampled(this.traceContext.sampled()).build();
	}

	@Override
	public TraceState getTraceState() {
		// TODO: Is this extra?
		return TraceState.builder().build();
	}

	@Override
	public boolean isRemote() {
		// TODO: ?
		return false;
	}

	public TraceContext unwrap() {
		return this.traceContext;
	}

}
