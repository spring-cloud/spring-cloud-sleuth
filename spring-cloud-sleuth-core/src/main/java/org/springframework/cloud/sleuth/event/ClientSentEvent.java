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
 * @author Dave Syer
 *
 */
@SuppressWarnings("serial")
public class ClientSentEvent extends ApplicationEvent {

	private final Span span;

	public ClientSentEvent(Object source, Span span) {
		super(source);
		this.span = span;
	}

	public Span getSpan() {
		return this.span;
	}

	public String toString() {
		return "org.springframework.cloud.sleuth.event.ClientSentEvent(span=" + this.span
				+ ")";
	}

	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof ClientSentEvent))
			return false;
		final ClientSentEvent other = (ClientSentEvent) o;
		if (!other.canEqual((Object) this))
			return false;
		final Object this$span = this.getSpan();
		final Object other$span = other.getSpan();
		if (this$span == null ? other$span != null : !this$span.equals(other$span))
			return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $span = this.getSpan();
		result = result * PRIME + ($span == null ? 0 : $span.hashCode());
		return result;
	}

	protected boolean canEqual(Object other) {
		return other instanceof ClientSentEvent;
	}
}