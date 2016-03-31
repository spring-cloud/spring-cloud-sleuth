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

import org.springframework.cloud.sleuth.Tracer;

import feign.RetryableException;
import feign.Retryer;

/**
 * Execution of this retryer means that an exception occurred while trying to send the
 * request. In that case we need to put information about this span into the
 * {@link FeignRequestContext} in order for the {@link feign.RequestInterceptor} to know
 * that it should be continued or a new one should be created.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class TraceFeignRetryer implements Retryer {

	private final Tracer tracer;
	private final FeignRequestContext feignRequestContext = FeignRequestContext
			.getInstance();
	private final Retryer delegate;

	TraceFeignRetryer(Tracer tracer) {
		this(tracer, new Retryer.Default());
	}

	TraceFeignRetryer(Tracer tracer, Retryer delegate) {
		this.tracer = tracer;
		this.delegate = delegate;
	}

	@Override
	public void continueOrPropagate(RetryableException e) {
		try {
			this.feignRequestContext.putSpan(this.tracer.getCurrentSpan(), true);
			this.tracer.getCurrentSpan().logEvent("feign.retry");
			this.delegate.continueOrPropagate(e);
		}
		catch (RetryableException e2) {
			this.tracer.close(this.tracer.getCurrentSpan());
			throw e2;
		}
	}

	@Override
	public Retryer clone() {
		return new TraceFeignRetryer(this.tracer);
	}
}