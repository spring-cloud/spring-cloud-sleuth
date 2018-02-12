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

import brave.http.HttpTracing;
import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;

/**
 * Lazilly resolves the Trace Feign Client
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
class LazyTracingFeignClient implements Client {

	private static final Log log = LogFactory.getLog(LazyTracingFeignClient.class);

	private Client tracingFeignClient;
	private HttpTracing httpTracing;
	private final BeanFactory beanFactory;
	private final Client delegate;

	LazyTracingFeignClient(BeanFactory beanFactory, Client delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override public Response execute(Request request, Request.Options options)
			throws IOException {
		if (log.isDebugEnabled()) {
			log.debug("Sending a request via tracing feign client");
		}
		return tracingFeignClient().execute(request, options);
	}

	private Client tracingFeignClient() {
		if (this.tracingFeignClient == null) {
			this.tracingFeignClient = TracingFeignClient.create(httpTracing(), this.delegate);
		}
		return this.tracingFeignClient;
	}

	private HttpTracing httpTracing() {
		if (this.httpTracing == null) {
			this.httpTracing = this.beanFactory.getBean(HttpTracing.class);
		}
		return this.httpTracing;
	}
}
