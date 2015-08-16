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

import static org.springframework.cloud.sleuth.util.ExceptionUtils.error;

import java.util.concurrent.Callable;

import org.springframework.cloud.sleuth.IdGenerator;
import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.NullScope;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceContextHolder;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.cloud.sleuth.event.SpanContinuedEvent;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.instrument.TraceCallable;
import org.springframework.cloud.sleuth.instrument.TraceRunnable;
import org.springframework.context.ApplicationEventPublisher;

/**
 * @author Spencer Gibb
 */
public class DefaultTrace implements Trace {

	private final Sampler<Void> defaultSampler;

	private final IdGenerator idGenerator;

	private final ApplicationEventPublisher publisher;

	public DefaultTrace(Sampler<Void> defaultSampler, IdGenerator idGenerator,
			ApplicationEventPublisher publisher) {
		this.defaultSampler = defaultSampler;
		this.idGenerator = idGenerator;
		this.publisher = publisher;
	}

	@Override
	public TraceScope startSpan(String name, Span parent) {
		if (parent == null) {
			return startSpan(name);
		}
		Span currentSpan = getCurrentSpan();
		if (currentSpan != null && !parent.equals(currentSpan)) {
			error("HTrace client error: thread " + Thread.currentThread().getName()
					+ " tried to start a new Span " + "with parent " + parent.toString()
					+ ", but there is already a " + "currentSpan " + currentSpan);
		}
		if (currentSpan==null) {
			TraceContextHolder.setCurrentSpan(parent);
		}
		return continueSpan(createChild(parent, name));
	}

	@Override
	public TraceScope startSpan(String name) {
		return this.startSpan(name, this.defaultSampler, null);
	}

	@Override
	public <T> TraceScope startSpan(String name, Sampler<T> s, T info) {
		Span span = null;
		if (TraceContextHolder.isTracing() || s.next(info)) {
			span = createChild(getCurrentSpan(), name);
		}
		return continueSpan(span);
	}

	protected Span createChild(Span parent, String name) {
		if (parent == null) {
			MilliSpan span = MilliSpan.builder().begin(System.currentTimeMillis()).name(name)
					.traceId(this.idGenerator.create()).spanId(this.idGenerator.create())
					.build();
			this.publisher.publishEvent(new SpanAcquiredEvent(this, span));
			return span;
		}
		else {
			MilliSpan span = MilliSpan.builder().begin(System.currentTimeMillis()).name(name)
					.traceId(parent.getTraceId()).parent(parent.getSpanId())
					.spanId(this.idGenerator.create()).processId(parent.getProcessId())
					.build();
			this.publisher.publishEvent(new SpanAcquiredEvent(this, parent, span));
			return span;
		}
	}

	@Override
	public TraceScope continueSpan(Span span) {
		// Return an empty TraceScope that does nothing on close
		if (span == null)
			return NullScope.INSTANCE;
		Span oldSpan = getCurrentSpan();
		TraceContextHolder.setCurrentSpan(span);
		this.publisher.publishEvent(new SpanContinuedEvent(this, span));
		return new TraceScope(this.publisher, span, oldSpan);
	}

	protected Span getCurrentSpan() {
		return TraceContextHolder.getCurrentSpan();
	}

	@Override
	public void addKVAnnotation(String key, String value) {
		Span s = getCurrentSpan();
		if (s != null) {
			s.addAnnotation(key, value);
		}
	}

	/**
	 * Wrap the callable in a TraceCallable, if tracing.
	 *
	 * @return The callable provided, wrapped if tracing, 'callable' if not.
	 */
	@Override
	public <V> Callable<V> wrap(Callable<V> callable) {
		if (TraceContextHolder.isTracing()) {
			return new TraceCallable<>(this, callable,
					TraceContextHolder.getCurrentSpan());
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
		if (TraceContextHolder.isTracing()) {
			return new TraceRunnable(this, runnable, TraceContextHolder.getCurrentSpan());
		}
		return runnable;
	}
}
