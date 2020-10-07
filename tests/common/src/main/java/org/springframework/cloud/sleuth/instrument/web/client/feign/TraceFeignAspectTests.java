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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import feign.Client;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;

/**
 * @author Marcin Grzejszczak
 */
@ExtendWith(MockitoExtension.class)
public abstract class TraceFeignAspectTests implements TestTracingAwareSupplier {

	@Mock
	BeanFactory beanFactory;

	@Mock
	Client client;

	@Mock
	ProceedingJoinPoint pjp;

	TraceFeignAspect traceFeignAspect;

	@BeforeEach
	public void setup() {
		this.traceFeignAspect = new TraceFeignAspect(this.beanFactory) {
			@Override
			Object executeTraceFeignClient(Object bean, ProceedingJoinPoint pjp) {
				return null;
			}
		};
	}

	@Test
	public void should_wrap_feign_client_in_trace_representation() throws Throwable {
		BDDMockito.given(this.pjp.getTarget()).willReturn(this.client);

		this.traceFeignAspect.feignClientWasCalled(this.pjp);

		Mockito.verify(this.pjp, Mockito.never()).proceed();
	}

	@Test
	public void should_not_wrap_traced_feign_client_in_trace_representation() throws Throwable {
		BDDMockito.given(this.pjp.getTarget()).willReturn(
				new TracingFeignClient(tracerTest().tracing().currentTraceContext(), tracerTest().tracing().httpClientHandler(), this.client));

		this.traceFeignAspect.feignClientWasCalled(this.pjp);

		Mockito.verify(this.pjp).proceed();
	}

}
