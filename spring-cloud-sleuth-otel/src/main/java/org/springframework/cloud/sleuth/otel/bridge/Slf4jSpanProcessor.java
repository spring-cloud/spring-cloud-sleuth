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

package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.MDC;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

public class Slf4jSpanProcessor implements SpanProcessor, ApplicationListener {

	private static final Log log = LogFactory.getLog(Slf4jSpanProcessor.class);

	@Override
	public void onStart(Context parent, ReadWriteSpan span) {
		onStart(span.getSpanContext().getTraceIdAsHexString(), span.getSpanContext().getSpanIdAsHexString());
	}

	private void onStart(String traceId, String spanId) {
		MDC.put("traceId", traceId);
		MDC.put("spanId", spanId);
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
		return CompletableResultCode.ofSuccess();
	}

	private void onScopeChanged(OtelCurrentTraceContext.ScopeChanged event) {
		if (log.isTraceEnabled()) {
			log.trace("Got scope changed event [" + event + "]");
		}
		if (event.span != null) {
			onStart(event.span.getSpanContext().getTraceIdAsHexString(),
					event.span.getSpanContext().getSpanIdAsHexString());
		}
	}

	private void onScopeClosed(OtelCurrentTraceContext.ScopeClosed event) {
		if (log.isTraceEnabled()) {
			log.trace("Got scope closed event [" + event + "]");
		}
		onEnd(null);
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof OtelCurrentTraceContext.ScopeChanged) {
			onScopeChanged((OtelCurrentTraceContext.ScopeChanged) event);
		}
		else if (event instanceof OtelCurrentTraceContext.ScopeClosed) {
			onScopeClosed((OtelCurrentTraceContext.ScopeClosed) event);
		}
	}

}
