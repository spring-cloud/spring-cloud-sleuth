/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.Objects;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import org.assertj.core.presentation.StandardRepresentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.BaseSubscriber;
import reactor.util.context.Context;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class ScopePassingSpanSubscriberTests {

	static {
		// AssertJ will recognise QueueSubscription implements queue and try to invoke
		// iterator. That's not allowed, and will cause an exception
		// Fuseable$QueueSubscription.NOT_SUPPORTED_MESSAGE.
		// This ensures AssertJ uses normal toString.
		StandardRepresentation.registerFormatterForType(ScopePassingSpanSubscriber.class,
				Objects::toString);
	}

	final CurrentTraceContext currentTraceContext = CurrentTraceContext.Default.create();

	TraceContext context = TraceContext.newBuilder().traceId(1).spanId(1).sampled(true)
			.build();

	TraceContext context2 = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true)
			.build();

	AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext();

	@AfterEach
	public void close() {
		springContext.close();
	}

	@Test
	public void should_propagate_current_context() {
		ScopePassingSpanSubscriber<?> subscriber = new ScopePassingSpanSubscriber<>(null,
				Context.of("foo", "bar"), this.currentTraceContext, null);

		then((String) subscriber.currentContext().get("foo")).isEqualTo("bar");
	}

	@Test
	public void should_set_empty_context_when_context_is_null() {
		ScopePassingSpanSubscriber<?> subscriber = new ScopePassingSpanSubscriber<>(null,
				Context.empty(), this.currentTraceContext, null);

		then(subscriber.currentContext().isEmpty()).isTrue();
	}

	@Test
	public void should_put_current_span_to_context() {
		try (Scope ws = this.currentTraceContext.newScope(context2)) {
			CoreSubscriber<?> subscriber = new ScopePassingSpanSubscriber<>(
					new BaseSubscriber<Object>() {
					}, Context.empty(), currentTraceContext, context);

			then(subscriber.currentContext().get(TraceContext.class)).isEqualTo(context);
		}
	}

}
