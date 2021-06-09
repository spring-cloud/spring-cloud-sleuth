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

package org.springframework.cloud.sleuth.instrument.session;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.cloud.sleuth.tracer.SimpleCurrentTraceContext;
import org.springframework.cloud.sleuth.tracer.SimpleSpan;
import org.springframework.cloud.sleuth.tracer.SimpleTracer;
import org.springframework.session.ReactiveSessionRepository;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceReactiveSessionRepositoryTests {

	SimpleCurrentTraceContext simpleCurrentTraceContext = new SimpleCurrentTraceContext();

	SimpleTracer simpleTracer = new SimpleTracer() {
		@Override
		public SimpleSpan nextSpan() {
			SimpleSpan span = super.nextSpan();
			simpleCurrentTraceContext.traceContext = span.context();
			return span;
		}
	};

	ReactiveSessionRepository delegate = mock(ReactiveSessionRepository.class);

	ReactiveSessionRepository traceSessionRepository = new TraceReactiveSessionRepository(this.simpleTracer,
			this.simpleCurrentTraceContext, delegate);

	@Test
	void should_trace_session_creation() {
		given(this.delegate.createSession()).willReturn(Mono.empty());

		this.traceSessionRepository.createSession().block(Duration.ofMillis(10));

		verify(this.delegate).createSession();
		then(this.simpleTracer.getLastSpan().name).isEqualTo(SleuthSessionSpan.SESSION_CREATE_SPAN.getName());
	}

	@Test
	void should_trace_session_save() {
		given(this.delegate.save(any())).willReturn(Mono.empty());

		this.traceSessionRepository.save(null).block(Duration.ofMillis(10));

		verify(this.delegate).save(any());
		then(this.simpleTracer.getLastSpan().name).isEqualTo(SleuthSessionSpan.SESSION_SAVE_SPAN.getName());
	}

	@Test
	void should_trace_session_find_by_id() {
		given(this.delegate.findById(any())).willReturn(Mono.empty());

		this.traceSessionRepository.findById("").block(Duration.ofMillis(10));

		verify(this.delegate).findById(any());
		then(this.simpleTracer.getLastSpan().name).isEqualTo(SleuthSessionSpan.SESSION_FIND_SPAN.getName());
	}

	@Test
	void should_trace_session_delete_by_id() {
		given(this.delegate.deleteById(any())).willReturn(Mono.empty());

		this.traceSessionRepository.deleteById("").block(Duration.ofMillis(10));

		verify(this.delegate).deleteById(any());
		then(this.simpleTracer.getLastSpan().name).isEqualTo(SleuthSessionSpan.SESSION_DELETE_SPAN.getName());
	}

}
