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

package org.springframework.cloud.sleuth.otel.log;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.slf4j.MDC;

class Slf4jSpanProcessor implements SpanProcessor {

	@Override
	public void onStart(ReadWriteSpan span) {
		MDC.put("traceId", span.getContext().getTraceId().toString());
		MDC.put("spanId", span.getContext().getSpanId().toString());
	}

	@Override
	public boolean isStartRequired() {
		return true;
	}

	@Override
	public void onEnd(ReadableSpan span) {
		MDC.remove("traceId");
		MDC.remove("spanId");
	}

	@Override
	public boolean isEndRequired() {
		return true;
	}

	@Override
	public CompletableResultCode shutdown() {
		onEnd(null);
		return CompletableResultCode.ofSuccess();
	}

	@Override
	public CompletableResultCode forceFlush() {
		onEnd(null);
		return CompletableResultCode.ofSuccess();
	}

}
