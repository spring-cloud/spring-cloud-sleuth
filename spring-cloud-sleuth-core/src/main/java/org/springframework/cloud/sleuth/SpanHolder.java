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

import java.util.Objects;

/**
 *
 * TODO: This class is not the best of solutions
 *
 * @author Marcin Grzejszczak
 */
public class SpanHolder {

	public static final String SPAN_NAME_HEADER = "span.name";

	public final Span span;
	private final boolean created;
	private final Tracer tracer;

	protected SpanHolder(Span span, boolean created, Tracer tracer) {
		this.span = span;
		this.created = created;
		this.tracer = tracer;
	}

	private SpanHolder(Tracer tracer) {
		this.span = null;
		this.created = false;
		this.tracer = tracer;
	}

	public static SpanHolder span(Tracer tracer) {
		return new SpanHolder(tracer);
	}

	/**
	 * TODO: Exists only to easily find executions in the code
	 */
	public static void tagWithSpanName(String spanName, Tracer tracer) {
		if (spanName != null) {
			tracer.addTag(SPAN_NAME_HEADER, spanName);
		}
	}

	public SpanHolder startOrContinueSpan(String spanName) {
		Span span = this.tracer.getCurrentSpan();
		return startOrContinueSpan(spanName, span);
	}

	public SpanHolder startOrContinueSpan(String spanName, Span span) {
		boolean created = false;
		if (span != null) {
			span = this.tracer.continueSpan(span);
		}
		else {
			span = this.tracer.startTrace(spanName);
			created = true;
		}
		SpanHolder newSpan = new SpanHolder(span, created, this.tracer);
		newSpan.tagSpanName(spanName);
		return newSpan;
	}

	private void tagSpanName(String spanName) {
		tagWithSpanName(spanName, this.tracer);
	}

	public Span closeOrDetach() {
		if (this.created) {
			return this.tracer.close(this.span);
		}
		else {
			return this.tracer.detach(this.span);
		}
	}

	public Span closeIfCreated() {
		if (this.created) {
			return this.tracer.close(this.span);
		}
		return this.span;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SpanHolder that = (SpanHolder) o;
		return this.created == that.created &&
				Objects.equals(this.span, that.span);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.span, this.created);
	}

	@Override public String toString() {
		return "SpanHolder{" +
				"span=" + this.span +
				", created=" + this.created +
				'}';
	}
}
