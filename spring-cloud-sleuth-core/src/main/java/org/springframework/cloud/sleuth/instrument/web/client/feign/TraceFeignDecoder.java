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
import java.lang.reflect.Type;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import feign.FeignException;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;

/**
 * A decoder that closes a span upon decoding the response.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
public class TraceFeignDecoder implements Decoder, ApplicationEventPublisherAware {

	private final Tracer tracer;
	private final FeignRequestContext feignRequestContext = FeignRequestContext.getInstance();
	private final Decoder delegate;
	private ApplicationEventPublisher publisher;

	public TraceFeignDecoder(Tracer tracer) {
		this(tracer, new Decoder.Default());
	}
	public TraceFeignDecoder(Tracer tracer, Decoder delegate) {
		this.tracer = tracer;
		this.delegate = delegate;
	}

	@Override
	public Object decode(Response response, Type type)
			throws IOException, DecodeException, FeignException {
		try {
			return this.delegate.decode(response, type);
		} finally {
			Span span = this.feignRequestContext.getCurrentSpan();
			if (span != null) {
				publish(new ClientReceivedEvent(this, span));
				this.tracer.close(span);
				this.feignRequestContext.clearContext();
			}
		}
	}

	private void publish(ApplicationEvent event) {
		if (this.publisher != null) {
			this.publisher.publishEvent(event);
		}
	}

	@Override
	public void setApplicationEventPublisher(
			ApplicationEventPublisher applicationEventPublisher) {
		this.publisher = applicationEventPublisher;
	}
}
