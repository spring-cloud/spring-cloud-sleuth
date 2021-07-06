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

package org.springframework.cloud.sleuth.test;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;

class TestSpanBuilder implements Span.Builder {

	private final Span.Builder delegate;

	private final TestTracer testTracer;

	TestSpanBuilder(Span.Builder delegate, TestTracer testTracer) {
		this.delegate = delegate;
		this.testTracer = testTracer;
	}

	@Override
	public Span.Builder setParent(TraceContext context) {
		delegate.setParent(context);
		return this;
	}

	@Override
	public Span.Builder setNoParent() {
		delegate.setNoParent();
		return this;
	}

	@Override
	public Span.Builder name(String name) {
		delegate.name(name);
		return this;
	}

	@Override
	public Span.Builder event(String value) {
		delegate.event(value);
		return this;
	}

	@Override
	public Span.Builder tag(String key, String value) {
		delegate.tag(key, value);
		return this;
	}

	@Override
	public Span.Builder error(Throwable throwable) {
		delegate.error(throwable);
		return this;
	}

	@Override
	public Span.Builder kind(Span.Kind spanKind) {
		delegate.kind(spanKind);
		return this;
	}

	@Override
	public Span.Builder remoteServiceName(String remoteServiceName) {
		delegate.remoteServiceName(remoteServiceName);
		return this;
	}

	@Override
	public Span start() {
		Span span = delegate.start();
		this.testTracer.createdSpans.add(span);
		return span;
	}

}
