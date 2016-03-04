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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.io.IOException;

import org.springframework.cloud.sleuth.Tracer;

import feign.Client;
import feign.Feign;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.hystrix.HystrixFeign;

/**
 * Contains {@link feign.Feign.Builder} implementation that delegates execution
 * {@link feign.hystrix.HystrixFeign}, with Sleuth {@link Retryer} and Sleuth
 * {@link Client} that close spans on exceptions / success and continues them on retries.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
class SleuthFeignBuilder {

	static Feign.Builder builder(Tracer tracer, FeignRequestContext feignRequestContext) {
		return HystrixFeign.builder()
				.retryer(new SleuthRetryer(tracer,feignRequestContext))
				.client(new SleuthClient(tracer, feignRequestContext));
	}

	/**
	 * Feign client that upon successful request execution clears the context
	 * and closes the span
	 */
	private static class SleuthClient implements Client {

		private final Client clientDelegate = new Client.Default(null, null);
		private final Tracer tracer;
		private final FeignRequestContext feignRequestContext;

		private SleuthClient(Tracer tracer, FeignRequestContext feignRequestContext) {
			this.tracer = tracer;
			this.feignRequestContext = feignRequestContext;
		}

		@Override
		public Response execute(Request request, Request.Options options)
				throws IOException {
			Response response = this.clientDelegate.execute(request, options);
			this.feignRequestContext.clearContext();
			this.tracer.close(this.tracer.getCurrentSpan());
			return response;
		}
	}

	/**
	 * Execution of this retryer means that an exception occurred while trying to
	 * send the request. In that case we need to put information about this span
	 * into the {@link FeignRequestContext} in order for the {@link feign.RequestInterceptor}
	 * to know that it should be continued or a new one should be created.
	 */
	private static class SleuthRetryer implements Retryer {

		private final Retryer delegate = new Retryer.Default();
		private final Tracer tracer;
		private final FeignRequestContext feignRequestContext;

		private SleuthRetryer(Tracer tracer, FeignRequestContext feignRequestContext) {
			this.tracer = tracer;
			this.feignRequestContext = feignRequestContext;
		}

		@Override
		public void continueOrPropagate(RetryableException e) {
			try {
				this.feignRequestContext.putSpan(this.tracer.getCurrentSpan(), true);
				this.tracer.getCurrentSpan().logEvent("retry");
				this.delegate.continueOrPropagate(e);
			} catch (RetryableException e2) {
				this.tracer.close(this.tracer.getCurrentSpan());
				throw e2;
			}
		}

		@Override
		public Retryer clone() {
			return new SleuthRetryer(this.tracer, this.feignRequestContext);
		}
	}
}
