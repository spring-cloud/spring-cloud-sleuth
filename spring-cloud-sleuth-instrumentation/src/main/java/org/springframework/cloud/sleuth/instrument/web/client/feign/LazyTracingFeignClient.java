/*
 * Copyright 2013-2021 the original author or authors.
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

import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.http.HttpClientHandler;

/**
 * Lazilly resolves the Trace Feign Client.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
class LazyTracingFeignClient implements Client {

	private static final Log log = LogFactory.getLog(LazyTracingFeignClient.class);

	private final BeanFactory beanFactory;

	private final Client delegate;

	private Client tracingFeignClient;

	private CurrentTraceContext currentTraceContext;

	private HttpClientHandler httpClientHandler;

	LazyTracingFeignClient(BeanFactory beanFactory, Client delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		if (log.isDebugEnabled()) {
			log.debug("Sending a request via tracing feign client [" + tracingFeignClient() + "] "
					+ "and the delegate [" + this.delegate + "]");
		}
		return tracingFeignClient().execute(request, options);
	}

	private Client tracingFeignClient() {
		if (this.tracingFeignClient == null) {
			this.tracingFeignClient = TracingFeignClient.create(currentTraceContext(), httpClientHandler(),
					this.delegate);
		}
		return this.tracingFeignClient;
	}

	private CurrentTraceContext currentTraceContext() {
		if (this.currentTraceContext == null) {
			this.currentTraceContext = this.beanFactory.getBean(CurrentTraceContext.class);
		}
		return this.currentTraceContext;
	}

	private HttpClientHandler httpClientHandler() {
		if (this.httpClientHandler == null) {
			this.httpClientHandler = this.beanFactory.getBean(HttpClientHandler.class);
		}
		return this.httpClientHandler;
	}

}
