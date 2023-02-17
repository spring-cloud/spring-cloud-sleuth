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

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.tracer.SimpleTracer;
import org.springframework.session.SessionRepository;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TraceSessionRepositoryAspectTests {

	@Test
	void should_throw_the_cause_of_the_session_exception() {
		SimpleTracer simpleTracer = new SimpleTracer();
		TraceSessionRepositoryAspect aspect = new TraceSessionRepositoryAspect(simpleTracer,
				simpleTracer.currentTraceContext()) {
			@Override
			Method getMethod(ProceedingJoinPoint pjp, Object tracingWrapper) {
				try {
					return SessionRepository.class.getMethod("createSession");
				}
				catch (NoSuchMethodException e) {
					throw new RuntimeException(e);
				}
			}
		};
		ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
		SessionRepository sessionRepository = mock(SessionRepository.class);
		given(pjp.getTarget()).willReturn(sessionRepository);
		given(sessionRepository.createSession()).willThrow(new RuntimeException("Boom!"));

		BDDAssertions.thenThrownBy(() -> aspect.wrapSessionRepository(pjp)).isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Boom!");
	}

}
