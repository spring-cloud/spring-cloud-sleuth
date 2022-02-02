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

package org.springframework.cloud.sleuth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

class ThreadLocalSpanTests {

	Tracer tracer = BDDMockito.mock(Tracer.class);

	@BeforeEach
	void setup() {
		given(this.tracer.withSpan(any())).willReturn(() -> {

		});
	}

	@Test
	void should_properly_stack_spans() {
		// given
		ThreadLocalSpan threadLocalSpan = new ThreadLocalSpan(tracer);
		then(threadLocalSpan.get()).isNull();

		// when - Span 1
		Span span = span();
		threadLocalSpan.set(span);
		// then - Span 1
		then(threadLocalSpan.get().getSpan()).isSameAs(span);

		// when - Span 2
		Span secondSpan = span();
		threadLocalSpan.set(secondSpan);
		// then - Span 2
		then(threadLocalSpan.get().getSpan()).isSameAs(secondSpan);

		// expect - Span 1
		threadLocalSpan.remove();
		then(threadLocalSpan.get().getSpan()).isSameAs(span);

		// expect - null
		threadLocalSpan.remove();
		then(threadLocalSpan.get()).isNull();
	}

	private Span span() {
		return BDDMockito.mock(Span.class);
	}

}
