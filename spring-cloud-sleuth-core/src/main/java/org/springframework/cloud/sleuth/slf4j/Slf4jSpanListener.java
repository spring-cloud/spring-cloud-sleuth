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

package org.springframework.cloud.sleuth.slf4j;

import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.event.SpanStartedEvent;
import org.springframework.cloud.sleuth.event.SpanStoppedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * @author Spencer Gibb
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class Slf4jSpanListener {

	@EventListener(SpanStartedEvent.class)
	public void start(SpanStartedEvent event) {
		Span span = event.getSpan();
		MDC.put(Trace.SPAN_ID_NAME, span.getSpanId());
		MDC.put(Trace.TRACE_ID_NAME, span.getTraceId());
		//TODO: what log level?
		log.info("Starting span: {}", span);
		if (event.getParent()!=null) {
			log.info("With parent: {}", event.getParent());
		}
	}

	@EventListener(SpanStoppedEvent.class)
	public void stop(SpanStoppedEvent event) {
		//TODO: what should this log level be?
		log.info("Stopped span: {}", event.getSpan());
		if (event.getParent()!=null) {
			log.info("With parent: {}", event.getParent());
		}
		MDC.remove(SPAN_ID_NAME);
		MDC.remove(TRACE_ID_NAME);
	}

}
