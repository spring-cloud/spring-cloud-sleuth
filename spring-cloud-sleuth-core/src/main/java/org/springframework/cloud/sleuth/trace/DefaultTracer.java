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

package org.springframework.cloud.sleuth.trace;

import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanContinuedEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.cloud.sleuth.instrument.TraceCallable;
import org.springframework.cloud.sleuth.instrument.TraceRunnable;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Random;
import java.util.concurrent.Callable;

import static org.springframework.cloud.sleuth.util.ExceptionUtils.warn;

/**
 * @author Spencer Gibb
 */
public class DefaultTracer implements Tracer {

	private final Sampler defaultSampler;

	private final ApplicationEventPublisher publisher;

	private final Random random;

	public DefaultTracer(Sampler defaultSampler,
						Random random, ApplicationEventPublisher publisher) {
		this.defaultSampler = defaultSampler;
		this.random = random;
		this.publisher = publisher;
	}

	@Override
	public Span joinTrace(String name, Span parent) {
		if (parent == null) {
			return startTrace(name);
		}
		Span currentSpan = getCurrentSpan();
		if (currentSpan != null && !parent.equals(currentSpan)) {
			warn("Warn during joining trace: thread " + Thread.currentThread().getName()
					+ " tried to start a new Span " + "with parent " + parent.toString()
					+ ", but there is already a " + "currentSpan " + currentSpan);
		}
		return continueSpan(createChild(parent, name));
	}

	@Override
	public Span startTrace(String name) {
		return this.startTrace(name, this.defaultSampler);
	}

	@Override
	public Span startTrace(String name, Sampler s) {
		Span span;
		if (isTracing() || s.isSampled()) {
			span = createChild(getCurrentSpan(), name);
		}
		else {
			// Non-exportable so we keep the trace but not other data
			long id = createId();
			span = Span.builder().begin(System.currentTimeMillis()).name(name)
					.traceId(id).spanId(id).exportable(false).build();
			this.publisher.publishEvent(new SpanAcquiredEvent(this, span));
		}
		return continueSpan(span);
	}

	@Override
	public Span detach(Span span) {
		if (span == null) {
			return null;
		}
		Span cur = SpanContextHolder.getCurrentSpan();
		if (cur != span) {
			ExceptionUtils.warn("Tried to detach trace span but "
					+ "it is not the current span for the '"
					+ Thread.currentThread().getName() + "' thread: " + span
					+ ". You have " + "probably forgotten to close or detach " + cur);
		}
		else {
			if (span.hasSavedSpan()) {
				SpanContextHolder.setCurrentSpan(span.getSavedSpan());
			}
			else {
				SpanContextHolder.removeCurrentSpan();
			}
		}
		return span.getSavedSpan();
	}

	@Override
	public Span close(Span span) {
		if (span == null) {
			return null;
		}
		Span cur = SpanContextHolder.getCurrentSpan();
		Span savedSpan = span.getSavedSpan();
		if (cur != span) {
			ExceptionUtils.warn("Tried to close trace span but "
					+ "it is not the current span for the '"
					+ Thread.currentThread().getName() + "' thread" + span
					+ ".  You have " + "probably forgotten to close or detach " + cur);
		}
		else {
			span.stop();
			if (savedSpan != null && span.getParents().contains(savedSpan.getSpanId())) {
				this.publisher.publishEvent(new SpanReleasedEvent(this, savedSpan, span));
				SpanContextHolder.setCurrentSpan(savedSpan);
			}
			else {
				if (!span.isRemote()) {
					this.publisher.publishEvent(new SpanReleasedEvent(this, span));
				}
				SpanContextHolder.removeCurrentSpan();
			}
		}
		return savedSpan;
	}

	protected Span createChild(Span parent, String name) {
		long id = createId();
		if (parent == null) {
			Span span = Span.builder().begin(System.currentTimeMillis())
					.name(name).traceId(id).spanId(id).build();
			this.publisher.publishEvent(new SpanAcquiredEvent(this, span));
			return span;
		}
		else {
			if (SpanContextHolder.getCurrentSpan() == null) {
				Span span = createSpan(null, parent);
				SpanContextHolder.setCurrentSpan(span);
			}
			Span span = Span.builder().begin(System.currentTimeMillis())
					.name(name).traceId(parent.getTraceId()).parent(parent.getSpanId())
					.spanId(id).processId(parent.getProcessId()).build();
			this.publisher.publishEvent(new SpanAcquiredEvent(this, parent, span));
			return span;
		}
	}

	private long createId() {
		return this.random.nextLong();
	}

	@Override
	public Span continueSpan(Span span) {
		if (span != null) {
			this.publisher.publishEvent(new SpanContinuedEvent(this, span));
		}
		Span newSpan = createSpan(SpanContextHolder.getCurrentSpan(), span);
		SpanContextHolder.setCurrentSpan(newSpan);
		return newSpan;
	}

	protected Span createSpan(Span saved, Span span) {
		return new Span(span, saved);
	}

	@Override
	public Span getCurrentSpan() {
		return SpanContextHolder.getCurrentSpan();
	}

	@Override
	public boolean isTracing() {
		return SpanContextHolder.isTracing();
	}

	@Override
	public void addTag(String key, String value) {
		Span s = getCurrentSpan();
		if (s != null && s.isExportable()) {
			s.tag(key, value);
		}
	}

	/**
	 * Wrap the callable in a TraceCallable, if tracing.
	 *
	 * @return The callable provided, wrapped if tracing, 'callable' if not.
	 */
	@Override
	public <V> Callable<V> wrap(Callable<V> callable) {
		if (isTracing()) {
			return new TraceCallable<>(this, callable);
		}
		return callable;
	}

	/**
	 * Wrap the runnable in a TraceRunnable, if tracing.
	 *
	 * @return The runnable provided, wrapped if tracing, 'runnable' if not.
	 */
	@Override
	public Runnable wrap(Runnable runnable) {
		if (isTracing()) {
			return new TraceRunnable(this, runnable);
		}
		return runnable;
	}

}
