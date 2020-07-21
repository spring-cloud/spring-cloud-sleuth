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

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import feign.Client;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author Marcin Grzejszczak
 */
@ExtendWith(MockitoExtension.class)
public class TraceFeignAspectTests {

	@Mock
	BeanFactory beanFactory;

	@Mock
	Client client;

	@Mock
	ProceedingJoinPoint pjp;

	StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(currentTraceContext)
			.build();

	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing).build();

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

	@AfterEach
	public void close() {
		this.tracing.close();
		this.currentTraceContext.close();
	}

	@Test
	public void should_wrap_feign_client_in_trace_representation() throws Throwable {
		given(this.pjp.getTarget()).willReturn(this.client);

		this.traceFeignAspect.feignClientWasCalled(this.pjp);

		verify(this.pjp, never()).proceed();
	}

	@Test
	public void should_not_wrap_traced_feign_client_in_trace_representation()
			throws Throwable {
		given(this.pjp.getTarget())
				.willReturn(new TracingFeignClient(this.httpTracing, this.client));

		this.traceFeignAspect.feignClientWasCalled(this.pjp);

		verify(this.pjp).proceed();
	}

}
