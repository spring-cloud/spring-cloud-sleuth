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

package org.springframework.cloud.sleuth;

import java.io.Closeable;

import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.NonFinal;

import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.ApplicationEventPublisher;

/**
 * @author Spencer Gibb
 */
@Value
@NonFinal
public class TraceScope implements Closeable {

	private final ApplicationEventPublisher publisher;

	/**
	 * the span for this scope
	 */
	private final Span span;

	/**
	 * the span that was "current" before this scope was entered
	 */
	private final Span savedSpan;

	@NonFinal
	private boolean detached = false;

	public TraceScope(ApplicationEventPublisher publisher, Span span, Span savedSpan) {
		this.publisher = publisher;
		this.span = span;
		this.savedSpan = savedSpan;
	}

	/**
	 * Remove this span as the current thread, but don't stop it yet or send it for
	 * collection. This is useful if the span object is then passed to another thread for
	 * use with Trace.continueTrace().
	 *
	 * @return the same Span object
	 */
	public Span detach() {
		if (this.detached) {
			ExceptionUtils.error("Tried to detach trace span but "
					+ "it has already been detached: " + this.span);
		}
		this.detached = true;

		Span cur = TraceContextHolder.getCurrentSpan();
		if (cur != this.span) {
			ExceptionUtils.error("Tried to detach trace span but "
					+ "it is not the current span for the '"
					+ Thread.currentThread().getName() + "' thread: " + this.span
					+ ". You have " + "probably forgotten to close or detach " + cur);
		}
		else {
			TraceContextHolder.setCurrentSpan(this.savedSpan);
		}
		return this.span;
	}

	@Override
	@SneakyThrows
	public void close() {
		if (this.detached) {
			return;
		}
		this.detached = true;
		Span cur = TraceContextHolder.getCurrentSpan();
		if (cur != this.span) {
			ExceptionUtils.error("Tried to close trace span but "
					+ "it is not the current span for the '"
					+ Thread.currentThread().getName() + "' thread" + this.span
					+ ".  You have " + "probably forgotten to close or detach " + cur);
		}
		else {
			this.span.stop();
			if (this.savedSpan != null
					&& this.span.getParents().contains(this.savedSpan.getSpanId())) {
				this.publisher.publishEvent(new SpanReleasedEvent(this, this.savedSpan,
						this.span));
			}
			else {
				this.publisher.publishEvent(new SpanReleasedEvent(this, this.span));
			}
			TraceContextHolder.setCurrentSpan(this.savedSpan);
		}
	}

}
