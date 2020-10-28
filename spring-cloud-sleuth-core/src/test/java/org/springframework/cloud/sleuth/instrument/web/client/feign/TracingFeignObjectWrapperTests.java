/*
 * Copyright 2013-2019 the original author or authors.
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
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancedRetryFactory;
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.loadbalancer.RetryableFeignBlockingLoadBalancerClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TracingFeignObjectWrapperTests {

	@Mock
	BeanFactory beanFactory;

	@InjectMocks
	TraceFeignObjectWrapper traceFeignObjectWrapper;

	@Test
	public void should_wrap_a_client_into_lazy_trace_client() {
		then(this.traceFeignObjectWrapper.wrap(mock(Client.class)))
				.isExactlyInstanceOf(LazyTracingFeignClient.class);
	}

	@Test
	public void should_not_wrap_a_bean_that_is_not_feign_related() {
		String notFeignRelatedObject = "object";
		then(this.traceFeignObjectWrapper.wrap(notFeignRelatedObject))
				.isSameAs(notFeignRelatedObject);
	}

	// gh-1528
	@Test
	public void should_wrap_feign_loadbalancer_client() {
		Client delegate = mock(Client.class);
		BlockingLoadBalancerClient loadBalancerClient = mock(
				BlockingLoadBalancerClient.class);
		when(beanFactory.getBean(BlockingLoadBalancerClient.class))
				.thenReturn(loadBalancerClient);

		Object wrapped = traceFeignObjectWrapper
				.wrap(new FeignBlockingLoadBalancerClient(delegate, loadBalancerClient));

		assertThat(wrapped).isInstanceOf(TraceFeignBlockingLoadBalancerClient.class);
	}

	// gh-1528
	@Test
	public void should_wrap_feign_retryable_loadbalancer_client() {
		Client delegate = mock(Client.class);
		BlockingLoadBalancerClient loadBalancerClient = mock(
				BlockingLoadBalancerClient.class);
		LoadBalancedRetryFactory retryFactory = mock(LoadBalancedRetryFactory.class);
		Mockito.when(beanFactory.getBean(BlockingLoadBalancerClient.class))
				.thenReturn(loadBalancerClient);

		Object wrapped = traceFeignObjectWrapper
				.wrap(new RetryableFeignBlockingLoadBalancerClient(delegate,
						loadBalancerClient, retryFactory));

		Assertions.assertThat(wrapped)
				.isInstanceOf(TraceRetryableFeignBlockingLoadBalancerClient.class);
	}

	// gh-1528, gh-1125
	@Test
	public void should_wrap_subclass_of_feign_loadbalancer_client() {
		Client delegate = mock(Client.class);
		BlockingLoadBalancerClient loadBalancerClient = mock(
				BlockingLoadBalancerClient.class);
		when(beanFactory.getBean(BlockingLoadBalancerClient.class))
				.thenReturn(loadBalancerClient);

		Object wrapped = traceFeignObjectWrapper.wrap(
				new TestFeignBlockingLoadBalancerClient(delegate, loadBalancerClient));

		assertThat(wrapped).isInstanceOf(TraceFeignBlockingLoadBalancerClient.class);

	}

	// gh-1528, gh-1125
	@Test
	public void should_wrap_subclass_of_retryable_feign_loadbalancer_client() {
		Client delegate = mock(Client.class);
		BlockingLoadBalancerClient loadBalancerClient = mock(
				BlockingLoadBalancerClient.class);
		LoadBalancedRetryFactory retryFactory = mock(LoadBalancedRetryFactory.class);
		Mockito.when(beanFactory.getBean(BlockingLoadBalancerClient.class))
				.thenReturn(loadBalancerClient);

		Object wrapped = traceFeignObjectWrapper
				.wrap(new TestRetryableFeignBlockingLoadBalancerClient(delegate,
						loadBalancerClient, retryFactory));

		Assertions.assertThat(wrapped)
				.isInstanceOf(TraceRetryableFeignBlockingLoadBalancerClient.class);
	}

	static class TestFeignBlockingLoadBalancerClient
			extends FeignBlockingLoadBalancerClient {

		TestFeignBlockingLoadBalancerClient(Client delegate,
				BlockingLoadBalancerClient loadBalancerClient) {
			super(delegate, loadBalancerClient);
		}

	}

	static class TestRetryableFeignBlockingLoadBalancerClient
			extends RetryableFeignBlockingLoadBalancerClient {

		TestRetryableFeignBlockingLoadBalancerClient(Client delegate,
				BlockingLoadBalancerClient loadBalancerClient,
				LoadBalancedRetryFactory retryFactory) {
			super(delegate, loadBalancerClient, retryFactory);
		}

	}

}
