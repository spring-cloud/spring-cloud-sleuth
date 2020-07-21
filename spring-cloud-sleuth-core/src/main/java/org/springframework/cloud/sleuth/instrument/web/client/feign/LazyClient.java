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

import java.io.IOException;

import brave.http.HttpTracing;
import feign.Client;
import feign.Request;
import feign.Response;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;

/**
 * Lazy implementation of the Feign Client.
 *
 * @author Marcin Grzejszczak
 */
class LazyClient implements Client {

	private final BeanFactory beanFactory;

	private Client delegate;

	private TraceFeignObjectWrapper wrapper;

	LazyClient(BeanFactory beanFactory, Client client) {
		this.beanFactory = beanFactory;
		this.delegate = client;
	}

	LazyClient(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		return ((Client) wrapper().wrap(delegate())).execute(request, options);
	}

	private Client delegate() {
		if (this.delegate == null) {
			try {
				this.delegate = this.beanFactory.getBean(Client.class);
			}
			catch (BeansException ex) {
				this.delegate = TracingFeignClient.create(
						beanFactory.getBean(HttpTracing.class),
						new Client.Default(null, null));
			}
		}
		return this.delegate;
	}

	private TraceFeignObjectWrapper wrapper() {
		if (this.wrapper == null) {
			this.wrapper = new TraceFeignObjectWrapper(this.beanFactory);
		}
		return this.wrapper;
	}

}
