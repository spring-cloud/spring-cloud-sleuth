/*
 * Copyright 2013-2016 the original author or authors.
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

import java.util.Objects;

import org.springframework.cloud.sleuth.Span;
import org.springframework.context.ApplicationEvent;

/**
 * @author Spencer Gibb
 */
@SuppressWarnings("serial")
public class ServerReceivedEvent extends ApplicationEvent {

	private final Span parent;
	private final Span span;

	public ServerReceivedEvent(Object source, Span span) {
		this(source, null, span);
	}

	public ServerReceivedEvent(Object source, Span parent, Span span) {
		super(source);
		this.parent = parent;
		this.span = span;
	}

	public Span getParent() {
		return this.parent;
	}

	public Span getSpan() {
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
		ServerReceivedEvent that = (ServerReceivedEvent) o;
		return Objects.equals(this.parent, that.parent) && Objects
				.equals(this.span, that.span);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.parent, this.span);
	}

	@Override
	public String toString() {
		return "ServerReceivedEvent{" +
				"parent=" + this.parent +
				", span=" + this.span +
				'}';
	}
}
