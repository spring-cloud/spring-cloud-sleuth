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

import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

/**
 * {@link SpanContextSupplier} that integrates Sleuth's {@link Tracer} with Prometheus
 * exemplars.
 *
 * @author Jonatan Ivanov
 * @since 3.1.0
 */
public class SleuthSpanContextSupplier implements SpanContextSupplier {

	private final Tracer tracer;

	/**
	 * @param tracer The tracer implementation to query for the current TraceId and
	 * SpanId.
	 */
	public SleuthSpanContextSupplier(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public String getTraceId() {
		Span span = tracer.currentSpan();
		return (span != null && span.context().sampled()) ? span.context().traceId() : null;
	}

	@Override
	public String getSpanId() {
		Span span = tracer.currentSpan();
		return (span != null && span.context().sampled()) ? span.context().spanId() : null;
	}

}
