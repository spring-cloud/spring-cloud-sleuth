/*
 * Copyright 2013-2015 the original author or authors.
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

import org.slf4j.MDC;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanContinuedEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Spencer Gibb
 */
@Slf4j
public class Slf4jSpanListener {

	@EventListener(SpanAcquiredEvent.class)
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void start(SpanAcquiredEvent event) {
		Span span = event.getSpan();
		MDC.put(Trace.SPAN_ID_NAME, Span.IdConverter.toHex(span.getSpanId()));
		MDC.put(Trace.SPAN_EXPORT_NAME, String.valueOf(span.isExportable()));
		MDC.put(Trace.TRACE_ID_NAME, Span.IdConverter.toHex(span.getTraceId()));
		log.trace("Starting span: {}", span);
		if (event.getParent() != null) {
			log.trace("With parent: {}", event.getParent());
		}
	}

	@EventListener(SpanContinuedEvent.class)
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void continued(SpanContinuedEvent event) {
		Span span = event.getSpan();
		MDC.put(Trace.SPAN_ID_NAME, Span.IdConverter.toHex(span.getSpanId()));
		MDC.put(Trace.TRACE_ID_NAME, Span.IdConverter.toHex(span.getTraceId()));
		MDC.put(Trace.SPAN_EXPORT_NAME, String.valueOf(span.isExportable()));
		log.trace("Continued span: {}", event.getSpan());
	}

	@EventListener(SpanReleasedEvent.class)
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void stop(SpanReleasedEvent event) {
		log.trace("Stopped span: {}", event.getSpan());
		if (event.getParent() != null) {
			log.trace("With parent: {}", event.getParent());
			MDC.put(Trace.SPAN_ID_NAME, Span.IdConverter.toHex(event.getParent().getSpanId()));
			MDC.put(Trace.SPAN_EXPORT_NAME, String.valueOf(event.getParent().isExportable()));
		}
		else {
			MDC.remove(Trace.SPAN_ID_NAME);
			MDC.remove(Trace.SPAN_EXPORT_NAME);
			MDC.remove(Trace.TRACE_ID_NAME);
		}
	}

}
