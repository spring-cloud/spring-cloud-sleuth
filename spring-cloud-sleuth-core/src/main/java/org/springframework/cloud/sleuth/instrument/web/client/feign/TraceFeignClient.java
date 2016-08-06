/*
 * Copyright 2013-2016 the original author or authors.
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

import java.io.IOException;
import java.net.URI;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;

import feign.Client;
import feign.Request;
import feign.Response;

/**
 * A Feign Client that closes a Span if there is no response body. In other cases Span
 * will get closed because the Decoder will be called
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class TraceFeignClient implements Client {

	private final Client delegate;
	private final BeanFactory beanFactory;
	private HttpTraceKeysInjector keysInjector;

	TraceFeignClient(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.delegate = new Client.Default(null, null);
	}

	TraceFeignClient(BeanFactory beanFactory, Client delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		Response response;
		addRequestTags(request);
		response = this.delegate.execute(request, options);
		return response;
	}

	/**
	 * Adds HTTP tags to the client side span
	 */
	private void addRequestTags(Request request) {
		URI uri = URI.create(request.url());
		getKeysInjector().addRequestTags(uri.toString(), uri.getHost(), uri.getPath(),
				request.method(), request.headers());
	}

	HttpTraceKeysInjector getKeysInjector() {
		if (this.keysInjector == null) {
			this.keysInjector = this.beanFactory.getBean(HttpTraceKeysInjector.class);
		}
		return this.keysInjector;
	}
}
