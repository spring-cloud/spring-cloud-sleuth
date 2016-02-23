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

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanContinuedEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Span listener that logs to the console when a span got
 * started / stopped / continued.
 *
 * @author Spencer Gibb
 *
 * @since 1.0.0
 */
public class Slf4jSpanListener {

	private final Logger log;
	private final Pattern nameSkipPattern;

	public Slf4jSpanListener(String nameSkipPattern) {
		this.nameSkipPattern = Pattern.compile(nameSkipPattern);
		this.log = org.slf4j.LoggerFactory
				.getLogger(Slf4jSpanListener.class);
	}

	Slf4jSpanListener(String nameSkipPattern, Logger log) {
		this.nameSkipPattern = Pattern.compile(nameSkipPattern);
		this.log = log;
	}

	@EventListener(SpanAcquiredEvent.class)
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void start(SpanAcquiredEvent event) {
		Span span = event.getSpan();
		MDC.put(Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
		MDC.put(Span.SPAN_EXPORT_NAME, String.valueOf(span.isExportable()));
		MDC.put(Span.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
		log("Starting span: {}", span);
		if (event.getParent() != null) {
			log("With parent: {}", event.getParent());
		}
	}

	@EventListener(SpanContinuedEvent.class)
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void continued(SpanContinuedEvent event) {
		Span span = event.getSpan();
		MDC.put(Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
		MDC.put(Span.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
		MDC.put(Span.SPAN_EXPORT_NAME, String.valueOf(span.isExportable()));
		log("Continued span: {}", event.getSpan());
	}

	@EventListener(SpanReleasedEvent.class)
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void stop(SpanReleasedEvent event) {
		log("Stopped span: {}", event.getSpan());
		if (event.getParent() != null) {
			log("With parent: {}", event.getParent());
			MDC.put(Span.SPAN_ID_NAME, Span.idToHex(event.getParent().getSpanId()));
			MDC.put(Span.SPAN_EXPORT_NAME, String.valueOf(event.getParent().isExportable()));
		}
		else {
			MDC.remove(Span.SPAN_ID_NAME);
			MDC.remove(Span.SPAN_EXPORT_NAME);
			MDC.remove(Span.TRACE_ID_NAME);
		}
	}

	private void log(String text, Span span) {
		if (this.nameSkipPattern.matcher(span.getName()).matches()) {
			return;
		}
		this.log.trace(text, span);
	}

}
