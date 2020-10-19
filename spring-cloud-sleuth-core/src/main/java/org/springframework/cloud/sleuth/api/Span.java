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

package org.springframework.cloud.sleuth.api;

/**
 * Taken mostly from Brave.
 */
public interface Span extends SpanCustomizer {

	boolean isNoop();

	TraceContext context();

	Span start();

	Span name(String name);

	Span event(String value);

	Span tag(String key, String value);

	Span error(Throwable throwable);

	void end();

	void abandon();

	enum Kind {

		CLIENT, SERVER, PRODUCER, CONSUMER

	}

	/**
	 * Used by extractors / injectors. Merge of Brave & Otel.
	 */
	interface Builder {

		Builder setParent(TraceContext context);

		Builder setNoParent();

		Builder name(String name);

		Builder event(String value);

		Builder tag(String key, String value);

		Builder error(Throwable throwable);

		Builder kind(Span.Kind spanKind);

		Builder remoteServiceName(String remoteServiceName);

		Span start();

	}

}
