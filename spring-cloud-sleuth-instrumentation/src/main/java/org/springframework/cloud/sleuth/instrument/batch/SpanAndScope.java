/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.batch;

import java.util.Objects;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

class SpanAndScope {

	final Span span;

	final Tracer.SpanInScope scope;

	SpanAndScope(Span span, Tracer.SpanInScope scope) {
		this.span = span;
		this.scope = scope;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SpanAndScope that = (SpanAndScope) o;
		return Objects.equals(this.span, that.span) && Objects.equals(this.scope, that.scope);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.span, this.scope);
	}

}
