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

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Abstract class for publishing {@link org.springframework.cloud.sleuth.event.ClientReceivedEvent}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
abstract class FeignEventPublisher {

	private final FeignRequestContext feignRequestContext = FeignRequestContext.getInstance();

	private final ApplicationEventPublisher publisher;
	private final Tracer tracer;

	protected FeignEventPublisher(ApplicationEventPublisher publisher, Tracer tracer) {
		this.publisher = publisher;
		this.tracer = tracer;
	}

	protected void finish() {
		Span span = this.feignRequestContext.getCurrentSpan();
		if (span != null) {
			this.publisher.publishEvent(new ClientReceivedEvent(this, span));
			this.tracer.close(span);
			this.feignRequestContext.clearContext();
		}
	}
}
