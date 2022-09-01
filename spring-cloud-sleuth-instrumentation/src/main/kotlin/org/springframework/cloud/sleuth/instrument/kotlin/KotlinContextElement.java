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

package org.springframework.cloud.sleuth.instrument.kotlin;

import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.ThreadContextElement;
import org.jetbrains.annotations.Nullable;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAndScope;
import org.springframework.cloud.sleuth.Tracer;

/**
 * {@link ThreadContextElement} for synchronizing a {@link SpanAndScope} across coroutine
 * suspension and resumption.
 *
 * Inspired by OpenTelemetry's KotlinContextElement.
 *
 * @since 3.1.0
 */
class KotlinContextElement implements ThreadContextElement<SpanAndScope> {

	static final CoroutineContext.Key<KotlinContextElement> KEY = new CoroutineContext.Key<KotlinContextElement>() {
	};

	private final Span span;

	private final Tracer tracer;

	KotlinContextElement(Tracer tracer) {
		this.tracer = tracer;
		this.span = tracer.currentSpan();
	}

	Span getSpan() {
		return this.span;
	}

	@Override
	public CoroutineContext.Key<?> getKey() {
		return KEY;
	}

	@Override
	@SuppressWarnings("MustBeClosedChecker")
	public SpanAndScope updateThreadContext(CoroutineContext coroutineContext) {
		Tracer.SpanInScope spanInScope = this.tracer.withSpan(this.span);
		return new SpanAndScope(this.span, spanInScope);
	}

	@Override
	public void restoreThreadContext(CoroutineContext coroutineContext, SpanAndScope spanAndScope) {
		Tracer.SpanInScope scope = spanAndScope.getScope();
		if (scope != null) {
			scope.close();
		}
	}

	@Override
	public CoroutineContext plus(CoroutineContext coroutineContext) {
		return CoroutineContext.DefaultImpls.plus(this, coroutineContext);
	}

	@Override
	public <R> R fold(R initial, Function2<? super R, ? super CoroutineContext.Element, ? extends R> operation) {
		return CoroutineContext.Element.DefaultImpls.fold(this, initial, operation);
	}

	@Nullable
	@Override
	public <E extends CoroutineContext.Element> E get(CoroutineContext.Key<E> key) {
		return CoroutineContext.Element.DefaultImpls.get(this, key);
	}

	@Override
	public CoroutineContext minusKey(CoroutineContext.Key<?> key) {
		return CoroutineContext.Element.DefaultImpls.minusKey(this, key);
	}

}
