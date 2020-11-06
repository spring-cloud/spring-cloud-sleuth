/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.async;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;

import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;

/**
 * @author Marcin Grzejszczak
 */
public abstract class TraceAsyncAspectTest implements TestTracingAwareSupplier {

	ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);

	@BeforeEach
	public void setup() throws NoSuchMethodException {
		MethodSignature signature = Mockito.mock(MethodSignature.class);
		BDDMockito.given(signature.getName()).willReturn("fooBar");
		BDDMockito.given(signature.getMethod()).willReturn(TraceAsyncAspectTest.class.getMethod("setup"));
		BDDMockito.given(this.point.getSignature()).willReturn(signature);
		BDDMockito.given(this.point.getTarget()).willReturn("");
	}

	// Issue#926
	@Test
	public void should_work() throws Throwable {
		TraceAsyncAspect asyncAspect = new TraceAsyncAspect(tracerTest().tracing().tracer(), new DefaultSpanNamer()) {
			@Override
			String name(ProceedingJoinPoint pjp) {
				return "foo-bar";
			}
		};

		asyncAspect.traceBackgroundThread(this.point);

		BDDAssertions.then(tracerTest().handler().reportedSpans()).hasSize(1);
		BDDAssertions.then(tracerTest().handler().reportedSpans().get(0).getName()).isEqualTo("foo-bar");
		BDDAssertions.then(tracerTest().handler().reportedSpans().get(0).getEndTimestamp()).isPositive();
	}

}
