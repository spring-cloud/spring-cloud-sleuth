/*
 * Copyright 2013-2021 the original author or authors.
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

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.docs.AssertingSpan;

/**
 * Brave implementation of a {@link Span}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class BraveSpan implements Span {

	final brave.Span delegate;

	public BraveSpan(brave.Span delegate) {
		this.delegate = delegate;
	}

	@Override
	public boolean isNoop() {
		return this.delegate.isNoop();
	}

	@Override
	public TraceContext context() {
		if (this.delegate == null) {
			return null;
		}
		return new BraveTraceContext(this.delegate.context());
	}

	@Override
	public Span start() {
		this.delegate.start();
		return this;
	}

	@Override
	public Span name(String name) {
		this.delegate.name(name);
		return this;
	}

	@Override
	public Span event(String value) {
		this.delegate.annotate(value);
		return this;
	}

	@Override
	public Span tag(String key, String value) {
		this.delegate.tag(key, value);
		return this;
	}

	@Override
	public Span error(Throwable throwable) {
		String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
		this.delegate.tag("error", message);
		this.delegate.error(throwable);
		return this;
	}

	@Override
	public void end() {
		this.delegate.finish();
	}

	@Override
	public void abandon() {
		this.delegate.abandon();
	}

	@Override
	public Span remoteServiceName(String remoteServiceName) {
		this.delegate.remoteServiceName(remoteServiceName);
		return this;
	}

	@Override
	public Span remoteIpAndPort(String ip, int port) {
		this.delegate.remoteIpAndPort(ip, port);
		return this;
	}

	@Override
	public String toString() {
		return this.delegate != null ? this.delegate.toString() : "null";
	}

	public static brave.Span toBrave(Span span) {
		BraveSpan unwrap = (BraveSpan) AssertingSpan.unwrap(span);
		if (unwrap == null) {
			return null;
		}
		return unwrap.delegate;
	}

	public static Span fromBrave(brave.Span span) {
		return new BraveSpan(span);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		Object unwrapped = o;
		if (o instanceof AssertingSpan) {
			unwrapped = ((AssertingSpan) o).getDelegate();
		}
		if (unwrapped == null || getClass() != unwrapped.getClass()) {
			return false;
		}
		BraveSpan braveSpan = (BraveSpan) unwrapped;
		return Objects.equals(this.delegate, braveSpan.delegate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.delegate);
	}

}
