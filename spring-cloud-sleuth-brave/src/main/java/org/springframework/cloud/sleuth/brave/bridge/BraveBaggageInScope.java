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

import brave.baggage.BaggageField;

import org.springframework.cloud.sleuth.api.BaggageInScope;
import org.springframework.cloud.sleuth.api.TraceContext;

/**
 * Brave implementation of a {@link BaggageInScope}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class BraveBaggageInScope implements BaggageInScope {

	private final BaggageField delegate;

	public BraveBaggageInScope(BaggageField delegate) {
		this.delegate = delegate;
	}

	@Override
	public String name() {
		return this.delegate.name();
	}

	@Override
	public String get() {
		return this.delegate.getValue();
	}

	@Override
	public String get(TraceContext traceContext) {
		return this.delegate.getValue(BraveTraceContext.toBrave(traceContext));
	}

	@Override
	public BraveBaggageInScope set(String value) {
		this.delegate.updateValue(value);
		return this;
	}

	public BaggageField unwrap() {
		return this.delegate;
	}

	@Override
	public BraveBaggageInScope set(TraceContext traceContext, String value) {
		this.delegate.updateValue(BraveTraceContext.toBrave(traceContext), value);
		return this;
	}

	@Override
	public void close() {

	}

}
