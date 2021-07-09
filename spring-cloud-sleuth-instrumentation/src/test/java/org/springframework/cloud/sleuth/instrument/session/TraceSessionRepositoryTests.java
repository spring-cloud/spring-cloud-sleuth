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

import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.tracer.SimpleTracer;
import org.springframework.session.SessionRepository;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceSessionRepositoryTests {

	SimpleTracer simpleTracer = new SimpleTracer();

	SessionRepository delegate = mock(SessionRepository.class);

	SessionRepository traceSessionRepository = new TraceSessionRepository(this.simpleTracer, delegate);

	@Test
	void should_trace_session_creation() {
		sessionRepository().createSession();

		verify(sessionRepositoryDelegate()).createSession();
		then(this.simpleTracer.getLastSpan().name).isEqualTo(SleuthSessionSpan.SESSION_CREATE_SPAN.getName());
	}

	@Test
	void should_trace_session_save() {
		sessionRepository().save(null);

		verify(sessionRepositoryDelegate()).save(any());
		then(this.simpleTracer.getLastSpan().name).isEqualTo(SleuthSessionSpan.SESSION_SAVE_SPAN.getName());
	}

	@Test
	void should_trace_session_find_by_id() {
		sessionRepository().findById("");

		verify(sessionRepositoryDelegate()).findById(any());
		then(this.simpleTracer.getLastSpan().name).isEqualTo(SleuthSessionSpan.SESSION_FIND_SPAN.getName());
	}

	@Test
	void should_trace_session_delete_by_id() {
		sessionRepository().deleteById("");

		verify(sessionRepositoryDelegate()).deleteById(any());
		then(this.simpleTracer.getLastSpan().name).isEqualTo(SleuthSessionSpan.SESSION_DELETE_SPAN.getName());
	}

	SessionRepository sessionRepository() {
		return this.traceSessionRepository;
	}

	SessionRepository sessionRepositoryDelegate() {
		return this.delegate;
	}

}
