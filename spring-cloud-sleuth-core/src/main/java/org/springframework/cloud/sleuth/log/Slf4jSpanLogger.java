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
import org.springframework.cloud.sleuth.TraceHeaders;

/**
 * Span listener that logs to the console when a span got
 * started / stopped / continued.
 *
 * @author Spencer Gibb
 *
 * @since 1.0.0
 */
public class Slf4jSpanLogger implements SpanLogger {

	private final Logger log;
	private final Pattern nameSkipPattern;
	private final TraceHeaders traceHeaders;

	public Slf4jSpanLogger(String nameSkipPattern, TraceHeaders traceHeaders) {
		this(nameSkipPattern, org.slf4j.LoggerFactory
				.getLogger(Slf4jSpanLogger.class), traceHeaders);
	}

	Slf4jSpanLogger(String nameSkipPattern, Logger log, TraceHeaders traceHeaders) {
		this.nameSkipPattern = Pattern.compile(nameSkipPattern);
		this.log = log;
		this.traceHeaders = traceHeaders;
	}

	@Override
	public void logStartedSpan(Span parent, Span span) {
		MDC.put(this.traceHeaders.getSpanId(), Span.idToHex(span.getSpanId()));
		MDC.put(this.traceHeaders.getSleuth().getExportable(),
				String.valueOf(span.isExportable()));
		MDC.put(this.traceHeaders.getTraceId(), Span.idToHex(span.getTraceId()));
		log("Starting span: {}", span);
		if (parent != null) {
			log("With parent: {}", parent);
		}
	}

	@Override
	public void logContinuedSpan(Span span) {
		MDC.put(this.traceHeaders.getSpanId(), Span.idToHex(span.getSpanId()));
		MDC.put(this.traceHeaders.getSleuth().getExportable(),
				String.valueOf(span.isExportable()));
		MDC.put(this.traceHeaders.getTraceId(), Span.idToHex(span.getTraceId()));
		log("Continued span: {}", span);
	}

	@Override
	public void logStoppedSpan(Span parent, Span span) {
		log("Stopped span: {}", span);
		if (parent != null) {
			log("With parent: {}", parent);
			MDC.put(this.traceHeaders.getSpanId(), Span.idToHex(span.getSpanId()));
			MDC.put(this.traceHeaders.getSleuth().getExportable(),
					String.valueOf(span.isExportable()));
		}
		else {
			MDC.remove(this.traceHeaders.getSpanId());
			MDC.remove(this.traceHeaders.getSleuth().getExportable());
			MDC.remove(this.traceHeaders.getTraceId());
		}
	}

	private void log(String text, Span span) {
		if (this.nameSkipPattern.matcher(span.getName()).matches()) {
			return;
		}
		this.log.trace(text, span);
	}

}
