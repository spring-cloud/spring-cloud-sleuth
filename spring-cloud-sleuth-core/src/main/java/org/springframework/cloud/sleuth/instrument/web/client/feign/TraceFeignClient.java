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
import java.util.Objects;

import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;

import feign.Client;
import feign.Request;
import feign.Response;
import feign.RetryableException;

/**
 * A Feign Client that closes a Span if there is no response body. In other cases Span
 * will get closed because the Decoder will be called
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class TraceFeignClient extends FeignEventPublisher implements Client {

	private final Client delegate;
	private final HttpTraceKeysInjector keysInjector;

	TraceFeignClient(Tracer tracer, HttpTraceKeysInjector keysInjector) {
		super(tracer);
		this.delegate = new Client.Default(null, null);
		this.keysInjector = keysInjector;
	}

	TraceFeignClient(Tracer tracer, Client delegate, HttpTraceKeysInjector keysInjector) {
		super(tracer);
		this.delegate = delegate;
		this.keysInjector = keysInjector;
	}

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		Response response;
		try {
			addRequestTags(request);
			response = this.delegate.execute(request, options);
		}
		catch (RetryableException | IOException e) {
			// IOException will be wrapped into a RetryableException in the caller
			throw e;
		}
		catch (RuntimeException e) {
			// Any other exception is going to be propagated so we need to tidy up
			finish();
			throw e;
		}
		if (response != null && response.body() == null || (response.body() != null
				&& Objects.equals(response.body().length(), 0))) {
			finish();
		}
		return response;
	}

	/**
	 * Adds HTTP tags to the client side span
	 */
	private void addRequestTags(Request request) {
		URI uri = URI.create(request.url());
		this.keysInjector.addRequestTags(uri.toString(), uri.getHost(), uri.getPath(),
				request.method(), request.headers());
	}
}
