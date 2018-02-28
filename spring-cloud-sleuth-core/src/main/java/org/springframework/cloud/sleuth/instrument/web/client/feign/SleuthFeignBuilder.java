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

import brave.http.HttpTracing;
import feign.Client;
import feign.Feign;
import feign.Retryer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;

/**
 * Contains {@link Feign.Builder} implementation with tracing components
 * that close spans on completion of request processing.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class SleuthFeignBuilder {

	private SleuthFeignBuilder() {}

	static Feign.Builder builder(BeanFactory beanFactory) {
		return Feign.builder().retryer(Retryer.NEVER_RETRY)
				.client(client(beanFactory));
	}

	private static Client client(BeanFactory beanFactory) {
		try {
			Client client = beanFactory.getBean(Client.class);
			return new LazyClient(beanFactory, client);
		} catch (BeansException e) {
			return TracingFeignClient.create(beanFactory.getBean(HttpTracing.class),
					new Client.Default(null, null));
		}
	}
}
