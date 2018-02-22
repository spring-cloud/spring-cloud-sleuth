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

package org.springframework.cloud.sleuth.instrument.web.client;

import org.assertj.core.api.BDDAssertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceWebClientBeanPostProcessorTest {

	@Mock BeanFactory beanFactory;

	@Test public void should_add_filter_only_once_to_web_client() {
		TraceWebClientBeanPostProcessor processor = new TraceWebClientBeanPostProcessor(this.beanFactory);
		WebClient client = WebClient.create();

		client = (WebClient) processor.postProcessAfterInitialization(client, "foo");
		client = (WebClient) processor.postProcessAfterInitialization(client, "foo");

		client.mutate().filters(filters -> {
			BDDAssertions.then(filters).hasSize(1);
			BDDAssertions.then(filters.get(0)).isInstanceOf(TraceExchangeFilterFunction.class);
		});
	}

	@Test public void should_add_filter_only_once_to_web_client_via_builder() {
		TraceWebClientBeanPostProcessor processor = new TraceWebClientBeanPostProcessor(this.beanFactory);
		WebClient.Builder builder = WebClient.builder();

		builder = (WebClient.Builder) processor.postProcessAfterInitialization(builder, "foo");
		builder = (WebClient.Builder) processor.postProcessAfterInitialization(builder, "foo");

		builder.build().mutate().filters(filters -> {
			BDDAssertions.then(filters).hasSize(1);
			BDDAssertions.then(filters.get(0)).isInstanceOf(TraceExchangeFilterFunction.class);
		});
	}
}