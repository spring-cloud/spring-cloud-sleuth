/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import feign.Client;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TracingFeignObjectWrapperTests {

	Tracing tracing = Tracing.newBuilder().build();
	HttpTracing httpTracing = HttpTracing.create(this.tracing);
	@Mock BeanFactory beanFactory;
	@InjectMocks TraceFeignObjectWrapper traceFeignObjectWrapper;

	@Test
	public void should_wrap_a_client_into_lazy_trace_client() throws Exception {
		then(this.traceFeignObjectWrapper.wrap(mock(Client.class))).isExactlyInstanceOf(LazyTracingFeignClient.class);
	}

	@Test
	public void should_not_wrap_a_bean_that_is_not_feign_related() throws Exception {
		String notFeignRelatedObject = "object";
		then(this.traceFeignObjectWrapper.wrap(notFeignRelatedObject)).isSameAs(notFeignRelatedObject);
	}
}