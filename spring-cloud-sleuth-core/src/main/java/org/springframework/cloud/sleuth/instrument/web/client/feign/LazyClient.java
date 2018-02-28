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

import java.io.IOException;

import feign.Client;
import feign.Request;
import feign.Response;
import org.springframework.beans.factory.BeanFactory;

class LazyClient implements Client {

	private final BeanFactory beanFactory;
	private final Client delegate;

	LazyClient(BeanFactory beanFactory, Client delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override public Response execute(Request request, Request.Options options)
			throws IOException {
		return ((Client) wrapper().wrap(this.delegate)).execute(request, options);
	}

	private TraceFeignObjectWrapper wrapper() {
		return new TraceFeignObjectWrapper(this.beanFactory);
	}
}
