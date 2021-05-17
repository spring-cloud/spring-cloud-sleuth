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

package org.springframework.cloud.sleuth.docs;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;

/**
 * In order to describe your spans via e.g. enums instead of Strings you can use this
 * interface that returns all the characteristics of a span. In Spring Cloud Sleuth we
 * analyze the sources and reuse this information to build a table of known spans, their
 * names, tags and events.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public interface AssertingSpanBuilder extends Span.Builder {

	/**
	 * @return a {@link DocumentedSpan} with span configuration
	 */
	DocumentedSpan getDocumentedSpan();

	/**
	 * @return wrapped {@link Span.Builder}
	 */
	Span.Builder getDelegate();

	@Override
	default AssertingSpanBuilder tag(String key, String value) {
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		getDelegate().tag(key, value);
		return this;
	}

	default AssertingSpanBuilder tag(TagKey key, String value) {
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		getDelegate().tag(key.getKey(), value);
		return this;
	}

	@Override
	default AssertingSpanBuilder event(String value) {
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		getDelegate().event(value);
		return this;
	}

	default AssertingSpanBuilder event(EventValue value) {
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		getDelegate().event(value.getValue());
		return this;
	}

	@Override
	default AssertingSpanBuilder name(String name) {
		DocumentedSpanAssertions.assertThatNameIsValid(name, getDocumentedSpan());
		getDelegate().name(name);
		return this;
	}

	@Override
	default AssertingSpanBuilder error(Throwable throwable) {
		getDelegate().error(throwable);
		return this;
	}

	@Override
	default AssertingSpanBuilder remoteServiceName(String remoteServiceName) {
		getDelegate().remoteServiceName(remoteServiceName);
		return this;
	}

	@Override
	default AssertingSpanBuilder setParent(TraceContext context) {
		getDelegate().setParent(context);
		return this;
	}

	@Override
	default AssertingSpanBuilder setNoParent() {
		getDelegate().setNoParent();
		return this;
	}

	@Override
	default AssertingSpanBuilder kind(Span.Kind spanKind) {
		getDelegate().kind(spanKind);
		return this;
	}

	@Override
	default AssertingSpan start() {
		Span span = getDelegate().start();
		DocumentedSpan documentedSpan = getDocumentedSpan();
		return new AssertingSpan() {
			@Override
			public DocumentedSpan getDocumentedSpan() {
				return documentedSpan;
			}

			@Override
			public Span getDelegate() {
				return span;
			}

			@Override
			public boolean isStarted() {
				return true;
			}
		};
	}

	static AssertingSpanBuilder of(DocumentedSpan documentedSpan, Span.Builder builder) {
		if (builder instanceof AssertingSpanBuilder) {
			return (AssertingSpanBuilder) builder;
		}
		return new ImmutableAssertingSpanBuilder(documentedSpan, builder);
	}

}
