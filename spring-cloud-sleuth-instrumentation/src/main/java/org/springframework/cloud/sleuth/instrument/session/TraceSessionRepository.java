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

import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

class TraceSessionRepository implements SessionRepository {

	private static final Log log = LogFactory.getLog(TraceSessionRepository.class);

	private final SessionRepository delegate;

	protected final Tracer tracer;

	TraceSessionRepository(Tracer tracer, SessionRepository delegate) {
		this.delegate = delegate;
		this.tracer = tracer;
	}

	@Override
	public Session createSession() {
		return wrap(SleuthSessionSpan.SESSION_CREATE_SPAN, (Supplier<Session>) this.delegate::createSession);
	}

	private <T> T wrap(SleuthSessionSpan sessionSpan, Supplier<T> supplier) {
		AssertingSpan span = newSpan(sessionSpan);
		if (log.isDebugEnabled()) {
			log.debug(
					"Wrapping call in a span with name [" + span.getDocumentedSpan().getName() + "] - [" + span + "]");
		}
		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			return supplier.get();
		}
		finally {
			span.end();
		}
	}

	private void wrap(SleuthSessionSpan sessionSpan, Runnable runnable) {
		wrap(sessionSpan, () -> {
			runnable.run();
			return null;
		});
	}

	private AssertingSpan newSpan(SleuthSessionSpan sessionCreateSpan) {
		return AssertingSpan.of(sessionCreateSpan, this.tracer.nextSpan()).name(sessionCreateSpan.getName());
	}

	@Override
	public void save(Session session) {
		wrap(SleuthSessionSpan.SESSION_SAVE_SPAN, () -> this.delegate.save(session));
	}

	@Override
	public Session findById(String id) {
		return wrap(SleuthSessionSpan.SESSION_FIND_SPAN, () -> this.delegate.findById(id));
	}

	@Override
	public void deleteById(String id) {
		wrap(SleuthSessionSpan.SESSION_DELETE_SPAN, () -> this.delegate.deleteById(id));
	}

}
