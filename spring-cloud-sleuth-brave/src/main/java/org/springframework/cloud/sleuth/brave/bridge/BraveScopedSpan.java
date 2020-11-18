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

package org.springframework.cloud.sleuth.brave.bridge;

import java.util.Objects;

import org.springframework.cloud.sleuth.api.ScopedSpan;
import org.springframework.cloud.sleuth.api.TraceContext;

/**
 * Brave implementation of a {@link ScopedSpan}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class BraveScopedSpan implements ScopedSpan {

	final brave.ScopedSpan span;

	BraveScopedSpan(brave.ScopedSpan span) {
		this.span = span;
	}

	@Override
	public boolean isNoop() {
		return this.span.isNoop();
	}

	@Override
	public TraceContext context() {
		return new BraveTraceContext(this.span.context());
	}

	@Override
	public ScopedSpan name(String name) {
		return new BraveScopedSpan(this.span.name(name));
	}

	@Override
	public ScopedSpan tag(String key, String value) {
		return new BraveScopedSpan(this.span.tag(key, value));
	}

	@Override
	public ScopedSpan event(String value) {
		return new BraveScopedSpan(this.span.annotate(value));
	}

	@Override
	public ScopedSpan error(Throwable throwable) {
		return new BraveScopedSpan(this.span.error(throwable));
	}

	@Override
	public void end() {
		this.span.finish();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		BraveScopedSpan that = (BraveScopedSpan) o;
		return Objects.equals(this.span, that.span);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.span);
	}

}
