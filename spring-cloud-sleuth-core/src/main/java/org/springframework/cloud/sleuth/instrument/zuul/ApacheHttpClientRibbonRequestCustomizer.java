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

package org.springframework.cloud.sleuth.instrument.zuul;

import brave.http.HttpClientAdapter;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import org.apache.http.Header;
import org.apache.http.client.methods.RequestBuilder;

/**
 * Customization of a Ribbon request for Apache HttpClient
 *
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
class ApacheHttpClientRibbonRequestCustomizer extends
		SpanInjectingRibbonRequestCustomizer<RequestBuilder> {

	static final Propagation.Setter<RequestBuilder, String> SETTER = new Propagation.Setter<RequestBuilder, String>() {
		@Override public void put(RequestBuilder carrier, String key, String value) {
			if (carrier.getFirstHeader(key) != null) {
				return;
			}
			carrier.addHeader(key, value);
		}

		@Override public String toString() {
			return "RequestBuilder::addHeader";
		}
	};

	ApacheHttpClientRibbonRequestCustomizer(HttpTracing tracer) {
		super(tracer);
	}

	@Override
	public boolean accepts(Class aClass) {
		return aClass == RequestBuilder.class;
	}

	@Override
	protected HttpClientAdapter<RequestBuilder, RequestBuilder> handlerClientAdapter() {
		return new HttpClientAdapter<RequestBuilder, RequestBuilder>() {
			@Override public String method(RequestBuilder request) {
				return request.getMethod();
			}

			@Override public String url(RequestBuilder request) {
				return request.getUri().toString();
			}

			@Override public String requestHeader(RequestBuilder request, String name) {
				Header header = request.getFirstHeader(name);
				if (header == null) {
					return null;
				}
				return header.getValue();
			}

			@Override public Integer statusCode(RequestBuilder response) {
				throw new UnsupportedOperationException("response not supported");
			}
		};
	}

	@Override protected Propagation.Setter<RequestBuilder, String> setter() {
		return SETTER;
	}
}
