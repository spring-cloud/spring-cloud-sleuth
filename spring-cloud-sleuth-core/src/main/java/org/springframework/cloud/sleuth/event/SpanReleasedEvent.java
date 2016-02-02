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

package org.springframework.cloud.sleuth.event;

import org.springframework.cloud.sleuth.Span;
import org.springframework.context.ApplicationEvent;

/**
 * @author Spencer Gibb
 */
@SuppressWarnings("serial")
public class SpanReleasedEvent extends ApplicationEvent {

	private final Span span;
	private final Span parent;

	public SpanReleasedEvent(Object source, Span span) {
		this(source, null, span);
	}

	public SpanReleasedEvent(Object source, Span parent, Span span) {
		super(source);
		this.parent = parent;
		this.span = span;
	}

	public Span getSpan() {
		return this.span;
	}

	public Span getParent() {
		return this.parent;
	}

	public String toString() {
		return "org.springframework.cloud.sleuth.event.SpanReleasedEvent(span="
				+ this.span + ", parent=" + this.parent + ")";
	}

	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof SpanReleasedEvent))
			return false;
		final SpanReleasedEvent other = (SpanReleasedEvent) o;
		if (!other.canEqual((Object) this))
			return false;
		final Object this$span = this.getSpan();
		final Object other$span = other.getSpan();
		if (this$span == null ? other$span != null : !this$span.equals(other$span))
			return false;
		final Object this$parent = this.getParent();
		final Object other$parent = other.getParent();
		if (this$parent == null ?
				other$parent != null :
				!this$parent.equals(other$parent))
			return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $span = this.getSpan();
		result = result * PRIME + ($span == null ? 0 : $span.hashCode());
		final Object $parent = this.getParent();
		result = result * PRIME + ($parent == null ? 0 : $parent.hashCode());
		return result;
	}

	protected boolean canEqual(Object other) {
		return other instanceof SpanReleasedEvent;
	}
}
