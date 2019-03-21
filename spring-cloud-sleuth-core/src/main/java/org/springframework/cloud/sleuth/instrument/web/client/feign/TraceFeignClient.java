/*
 * Copyright 2013-2017 the original author or authors.
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
import feign.Request;
import feign.Response;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpSpanInjector;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.util.SpanNameUtil;

/**
 * A Feign Client that closes a Span if there is no response body. In other cases Span
 * will get closed because the Decoder will be called
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
class TraceFeignClient implements Client {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final Client delegate;
	private HttpTraceKeysInjector keysInjector;
	private final BeanFactory beanFactory;
	private Tracer tracer;
	private HttpSpanInjector spanInjector;
	private ErrorParser errorParser;

	TraceFeignClient(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.delegate = client(beanFactory);
	}

	private Client client(BeanFactory beanFactory) {
		try {
			return beanFactory.getBean(Client.class);
		} catch (NoSuchBeanDefinitionException e) {
			return new Client.Default(null, null);
		}
	}

	TraceFeignClient(BeanFactory beanFactory, Client delegate) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
	}

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		String spanName = getSpanName(request);
		Span span = getTracer().createSpan(spanName);
		if (log.isDebugEnabled()) {
			log.debug("Created new Feign span " + span);
		}
		try {
			AtomicReference<Request> feignRequest = new AtomicReference<>(request);
			spanInjector().inject(span, new FeignRequestTextMap(feignRequest));
			span.logEvent(Span.CLIENT_SEND);
			addRequestTags(request);
			Request modifiedRequest = feignRequest.get();
			if (log.isDebugEnabled()) {
				log.debug("The modified request equals " + modifiedRequest);
			}
			Response response = this.delegate.execute(modifiedRequest, options);
			logCr();
			return response;
		} catch (RuntimeException | IOException e) {
			logCr();
			logError(e);
			throw e;
		} finally {
			closeSpan(span);
		}
	}

	private String getSpanName(Request request) {
		URI uri = URI.create(request.url());
		return SpanNameUtil.shorten(uriScheme(uri) + ":" + uri.getPath());
	}

	private String uriScheme(URI uri) {
		return uri.getScheme() == null ? "http" : uri.getScheme();
	}

	/**
	 * Adds HTTP tags to the client side span
	 */
	private void addRequestTags(Request request) {
		URI uri = URI.create(request.url());
		keysInjector().addRequestTags(uri.toString(), uri.getHost(), uri.getPath(),
				request.method(), request.headers());
	}

	private HttpTraceKeysInjector keysInjector() {
		if (this.keysInjector == null) {
			this.keysInjector = this.beanFactory.getBean(HttpTraceKeysInjector.class);
		}
		return this.keysInjector;
	}

	private HttpSpanInjector spanInjector() {
		if (this.spanInjector == null) {
			this.spanInjector = this.beanFactory.getBean(HttpSpanInjector.class);
		}
		return this.spanInjector;
	}

	private void closeSpan(Span span) {
		if (span != null) {
			if (log.isDebugEnabled()) {
				log.debug("Closing Feign span " + span);
			}
			getTracer().close(span);
		}
	}

	private void logCr() {
		Span span = getTracer().getCurrentSpan();
		if (span != null) {
			if (log.isDebugEnabled()) {
				log.debug("Closing Feign span and logging CR " + span);
			}
			span.logEvent(Span.CLIENT_RECV);
		}
	}

	private void logError(Exception e) {
		Span span = getTracer().getCurrentSpan();
		if (span != null) {
			getErrorParser().parseErrorTags(span, e);
		}
	}

	private Tracer getTracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	private ErrorParser getErrorParser() {
		if (this.errorParser == null) {
			this.errorParser = this.beanFactory.getBean(ErrorParser.class);
		}
		return this.errorParser;
	}
}
