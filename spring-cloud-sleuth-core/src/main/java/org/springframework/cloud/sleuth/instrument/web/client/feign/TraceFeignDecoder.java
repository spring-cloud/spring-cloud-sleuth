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

import org.springframework.beans.factory.BeanFactory;

import feign.FeignException;
import feign.Response;
import feign.codec.Decoder;

/**
 * A decoder that closes a span upon decoding the response.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class TraceFeignDecoder extends FeignEventPublisher implements Decoder {

	private final Decoder delegate;

	TraceFeignDecoder(BeanFactory beanFactory) {
		super(beanFactory);
		this.delegate = new Decoder.Default();
	}

	TraceFeignDecoder(BeanFactory beanFactory, Decoder delegate) {
		super(beanFactory);
		this.delegate = delegate;
	}

	@Override
	public Object decode(Response response, Type type)
			throws IOException, FeignException {
		try {
			return this.delegate.decode(response, type);
		} finally {
			finish();
		}
	}
}
