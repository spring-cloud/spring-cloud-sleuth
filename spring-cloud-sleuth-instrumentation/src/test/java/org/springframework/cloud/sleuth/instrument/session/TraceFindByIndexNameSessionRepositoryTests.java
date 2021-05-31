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

import org.springframework.cloud.sleuth.tracer.SimpleSpan;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.SessionRepository;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceFindByIndexNameSessionRepositoryTests extends TraceSessionRepositoryTests {

	FindByIndexNameSessionRepository delegate = mock(FindByIndexNameSessionRepository.class);

	TraceFindByIndexNameSessionRepository traceSessionRepository = new TraceFindByIndexNameSessionRepository(
			this.simpleTracer, this.delegate);

	@Test
	void should_trace_session_find_by_principle_name() {
		this.traceSessionRepository.findByPrincipalName("foo");

		verify(this.delegate).findByPrincipalName(any());
		SimpleSpan lastSpan = this.simpleTracer.getLastSpan();
		then(lastSpan.name).isEqualTo(SleuthSessionSpan.SESSION_FIND_SPAN.getName());
		then(lastSpan.tags).containsEntry(SleuthSessionSpan.Tags.PRINCIPAL_NAME.getKey(), "foo");
	}

	@Test
	void should_trace_session_find_by_index_name_value() {
		this.traceSessionRepository.findByIndexNameAndIndexValue("indexName1", "indexValue1");

		verify(this.delegate).findByIndexNameAndIndexValue(any(), any());
		SimpleSpan lastSpan = this.simpleTracer.getLastSpan();
		then(lastSpan.name).isEqualTo(SleuthSessionSpan.SESSION_FIND_SPAN.getName());
		then(lastSpan.tags).containsEntry(SleuthSessionSpan.Tags.INDEX_NAME.getKey(), "indexName1")
				.containsEntry(SleuthSessionSpan.Tags.INDEX_VALUE.getKey(), "indexValue1");
	}

	@Override
	SessionRepository sessionRepositoryDelegate() {
		return this.delegate;
	}

	@Override
	SessionRepository sessionRepository() {
		return this.traceSessionRepository;
	}

}
