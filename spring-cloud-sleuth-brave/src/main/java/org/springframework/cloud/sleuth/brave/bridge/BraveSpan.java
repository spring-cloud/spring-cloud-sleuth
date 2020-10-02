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

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.TraceContext;

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
	public SpanCustomizer customizer() {
		return new BraveSpanCustomizer(this.delegate.customizer());
	}

	@Override
	public Span start() {
		return new BraveSpan(this.delegate.start());
	}

	@Override
	public Span start(long timestamp) {
		return new BraveSpan(this.delegate.start(timestamp));
	}

	@Override
	public Span name(String name) {
		return new BraveSpan(this.delegate.name(name));
	}

	@Override
	public Span kind(Kind kind) {
		return new BraveSpan(this.delegate.kind(kind != null ? brave.Span.Kind.valueOf(kind.toString()) : null));
	}

	@Override
	public Span annotate(String value) {
		return new BraveSpan(this.delegate.annotate(value));
	}

	@Override
	public Span annotate(long timestamp, String value) {
		return new BraveSpan(this.delegate.annotate(timestamp, value));
	}

	@Override
	public Span tag(String key, String value) {
		return new BraveSpan(this.delegate.tag(key, value));
	}

	@Override
	public Span error(Throwable throwable) {
		String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
		this.delegate.tag("error", message);
		this.delegate.error(throwable);
		return new BraveSpan(this.delegate);
	}

	@Override
	public Span remoteServiceName(String remoteServiceName) {
		return new BraveSpan(this.delegate.remoteServiceName(remoteServiceName));
	}

	@Override
	public boolean remoteIpAndPort(String remoteIp, int remotePort) {
		return this.delegate.remoteIpAndPort(remoteIp, remotePort);
	}

	@Override
	public void finish() {
		this.delegate.finish();
	}

	@Override
	public void abandon() {
		this.delegate.abandon();
	}

	@Override
	public void finish(long timestamp) {
		this.delegate.finish(timestamp);
	}

	@Override
	public void flush() {
		this.delegate.flush();
	}

	@Override
	public String toString() {
		return this.delegate != null ? this.delegate.toString() : "null";
	}

	public static brave.Span toBrave(Span span) {
		return ((BraveSpan) span).delegate;
	}

	public static Span fromBrave(brave.Span span) {
		return new BraveSpan(span);
	}
}
