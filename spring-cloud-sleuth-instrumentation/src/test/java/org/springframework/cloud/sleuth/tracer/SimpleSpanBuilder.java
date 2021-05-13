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

package org.springframework.cloud.sleuth.tracer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;

/**
 * A noop implementation. Does nothing.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class SimpleSpanBuilder implements Span.Builder {

	List<String> events = new ArrayList<>();

	Map<String, String> tags = new HashMap<>();

	Throwable error;

	Span.Kind spanKind;

	String remoteServiceName;

	String name;

	SimpleTracer simpleTracer;

	public SimpleSpanBuilder(SimpleTracer simpleTracer) {
		this.simpleTracer = simpleTracer;
	}

	@Override
	public Span.Builder setParent(TraceContext context) {
		return this;
	}

	@Override
	public Span.Builder setNoParent() {
		return this;
	}

	@Override
	public Span.Builder name(String name) {
		this.name = name;
		return this;
	}

	@Override
	public Span.Builder event(String value) {
		this.events.add(value);
		return this;
	}

	@Override
	public Span.Builder tag(String key, String value) {
		this.tags.put(key, value);
		return this;
	}

	@Override
	public Span.Builder error(Throwable throwable) {
		this.error = throwable;
		return this;
	}

	@Override
	public Span.Builder kind(Span.Kind spanKind) {
		this.spanKind = spanKind;
		return this;
	}

	@Override
	public Span.Builder remoteServiceName(String remoteServiceName) {
		this.remoteServiceName = remoteServiceName;
		return this;
	}

	@Override
	public Span start() {
		SimpleSpan span = new SimpleSpan();
		this.tags.forEach(span::tag);
		this.events.forEach(span::event);
		span.remoteServiceName(this.remoteServiceName);
		span.error(this.error);
		span.spanKind = this.spanKind;
		span.name(this.name);
		span.start();
		simpleTracer.spans.add(span);
		return span;
	}

}
