/*
 * Copyright 2018-2021 the original author or authors.
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

import java.util.Map;

import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.session.FindByIndexNameSessionRepository;

class TraceFindByIndexNameSessionRepository extends TraceSessionRepository implements FindByIndexNameSessionRepository {

	private final FindByIndexNameSessionRepository delegate;

	TraceFindByIndexNameSessionRepository(Tracer tracer, FindByIndexNameSessionRepository delegate) {
		super(tracer, delegate);
		this.delegate = delegate;
	}

	@Override
	public Map findByPrincipalName(String principalName) {
		AssertingSpan span = newSessionFindSpan();
		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			return this.delegate.findByPrincipalName(principalName);
		}
		finally {
			span.end();
		}
	}

	private AssertingSpan newSessionFindSpan() {
		return AssertingSpan.of(SleuthSessionSpan.SESSION_FIND_SPAN, this.tracer.nextSpan())
				.name(SleuthSessionSpan.SESSION_FIND_SPAN.getName());
	}

	@Override
	public Map findByIndexNameAndIndexValue(String indexName, String indexValue) {
		AssertingSpan span = newSessionFindSpan();
		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			span.tag(SleuthSessionSpan.Tags.INDEX_NAME, indexName);
			return this.delegate.findByIndexNameAndIndexValue(indexName, indexValue);
		}
		finally {
			span.end();
		}
	}

}
