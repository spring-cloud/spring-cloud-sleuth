/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.reactor;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.util.context.Context;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class ScopePassingSpanSubscriberTests {

	Tracing tracing = Tracing.newBuilder().build();

	@Test
	public void should_propagate_current_context() {
		ScopePassingSpanSubscriber subscriber = new ScopePassingSpanSubscriber(
				null, Context.of("foo", "bar"), this.tracing);

		then((String) subscriber.currentContext().get("foo")).isEqualTo("bar");
	}

	@Test
	public void should_set_empty_context_when_context_is_null() {
		ScopePassingSpanSubscriber subscriber = new ScopePassingSpanSubscriber(
				null, null, this.tracing);

		then(subscriber.currentContext().isEmpty()).isTrue();
	}

	@Test
	public void should_put_current_span_to_context() {
		Span span = this.tracing.tracer().nextSpan();
		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span.start())) {
			ScopePassingSpanSubscriber subscriber = new ScopePassingSpanSubscriber(
					null, Context.empty(), this.tracing);

			then(subscriber.currentContext().get(Span.class)).isEqualTo(span);
		}

	}
}