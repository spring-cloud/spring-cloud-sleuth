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
import java.lang.invoke.MethodHandles;
import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
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

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final Client delegate;
	private HttpTraceKeysInjector keysInjector;
	private final BeanFactory beanFactory;
	private Tracer tracer;

	TraceFeignClient(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.delegate = new Client.Default(null, null);
	}

	TraceFeignClient(BeanFactory beanFactory, Client delegate) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
	}

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		try {
			addRequestTags(request);
			Response response = this.delegate.execute(request, options);
			logCr();
			return response;
		} catch (RuntimeException | IOException e) {
			logError(e);
			throw e;
		} finally {
			closeSpan();
		}
	}

	/**
	 * Adds HTTP tags to the client side span
	 */
	private void addRequestTags(Request request) {
		URI uri = URI.create(request.url());
		getKeysInjector().addRequestTags(uri.toString(), uri.getHost(), uri.getPath(),
				request.method(), request.headers());
	}

	private HttpTraceKeysInjector getKeysInjector() {
		if (this.keysInjector == null) {
			this.keysInjector = this.beanFactory.getBean(HttpTraceKeysInjector.class);
		}
		return this.keysInjector;
	}

	private void closeSpan() {
		Span span = getTracer().getCurrentSpan();
		if (span != null) {
			log.debug("Closing Feign span " + span);
			getTracer().close(span);
		}
	}

	private void logCr() {
		Span span = getTracer().getCurrentSpan();
		if (span != null) {
			log.debug("Closing Feign span and logging CR" + span);
			span.logEvent(Span.CLIENT_RECV);
		}
	}

	private void logError(Exception e) {
		Span span = getTracer().getCurrentSpan();
		if (span != null) {
			String message = e.getMessage() != null ? e.getMessage() : e.toString();
			log.debug("Appending exception [" + message + "] to span "  + span);
			getTracer().addTag("error", message);
		}
	}

	private Tracer getTracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}
}
