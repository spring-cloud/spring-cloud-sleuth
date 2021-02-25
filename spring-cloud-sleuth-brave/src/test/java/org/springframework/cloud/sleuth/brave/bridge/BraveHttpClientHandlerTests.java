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

package org.springframework.cloud.sleuth.brave.bridge;

import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import org.junit.jupiter.api.Test;

class BraveHttpClientHandlerTests {

	@Test
	void should_not_throw_exception_when_response_null() {
		Tracing tracing = Tracing.newBuilder().build();
		brave.http.HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> delegate = HttpClientHandler
				.create(HttpTracing.newBuilder(tracing).build());
		BraveHttpClientHandler handler = new BraveHttpClientHandler(delegate);

		handler.handleReceive(null, new BraveSpan(tracing.currentTracer().nextSpan()));
	}

}
