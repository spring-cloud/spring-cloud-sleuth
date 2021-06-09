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

import reactor.core.publisher.Mono;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;

class TraceReactiveSessionRepository implements ReactiveSessionRepository {

	private final ReactiveSessionRepository delegate;

	private final Tracer tracer;

	private final CurrentTraceContext currentTraceContext;

	TraceReactiveSessionRepository(Tracer tracer, CurrentTraceContext currentTraceContext,
			ReactiveSessionRepository delegate) {
		this.delegate = delegate;
		this.tracer = tracer;
		this.currentTraceContext = currentTraceContext;
	}

	@Override
	public Mono createSession() {
		return ReactorSleuth.tracedMono(this.tracer, this.currentTraceContext,
				SleuthSessionSpan.SESSION_CREATE_SPAN.getName(), () -> this.delegate.createSession());
	}

	@Override
	public Mono<Void> save(Session session) {
		return ReactorSleuth.tracedMono(this.tracer, this.currentTraceContext,
				SleuthSessionSpan.SESSION_SAVE_SPAN.getName(), () -> this.delegate.save(session));
	}

	@Override
	public Mono findById(String id) {
		return ReactorSleuth.tracedMono(this.tracer, this.currentTraceContext,
				SleuthSessionSpan.SESSION_FIND_SPAN.getName(), () -> this.delegate.findById(id));
	}

	@Override
	public Mono<Void> deleteById(String id) {
		return ReactorSleuth.tracedMono(this.tracer, this.currentTraceContext,
				SleuthSessionSpan.SESSION_DELETE_SPAN.getName(), () -> this.delegate.deleteById(id));
	}

}
