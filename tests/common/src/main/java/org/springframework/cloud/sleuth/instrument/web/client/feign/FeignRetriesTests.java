/*
 * Copyright 2013-2020 the original author or authors.
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import feign.Client;
import feign.Feign;
import feign.FeignException;
import feign.Request;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Response;
import okhttp3.mockwebserver.MockWebServer;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;

/**
 * @author Marcin Grzejszczak
 */
@ExtendWith(MockitoExtension.class)
public abstract class FeignRetriesTests implements TestTracingAwareSupplier {

	public final MockWebServer server = new MockWebServer();

	@BeforeEach
	void before() throws IOException {
		this.server.start();
	}

	@AfterEach
	void after() throws IOException {
		this.server.close();
	}

	@Mock(lenient = true)
	BeanFactory beanFactory;

	@BeforeEach
	@AfterEach
	public void setup() {
		BDDMockito.given(this.beanFactory.getBean(CurrentTraceContext.class))
				.willReturn(tracerTest().tracing().currentTraceContext());
		BDDMockito.given(this.beanFactory.getBean(HttpClientHandler.class))
				.willReturn(tracerTest().tracing().httpClientHandler());
	}

	@Test
	public void testRetriedWhenExceededNumberOfRetries() {
		Client client = (request, options) -> {
			throw new IOException();
		};
		String url = "http://localhost:" + this.server.getPort();

		TestInterface api = Feign.builder().client(new TracingFeignClient(tracerTest().tracing().currentTraceContext(),
				tracerTest().tracing().httpClientHandler(), client)).target(TestInterface.class, url);

		try {
			api.decodedPost();
			Assertions.failBecauseExceptionWasNotThrown(FeignException.class);
		}
		catch (FeignException e) {
		}
	}

	@Test
	public void testRetriedWhenRequestEventuallyIsSent() {
		String url = "http://localhost:" + this.server.getPort();
		final AtomicInteger atomicInteger = new AtomicInteger();
		// Client to simulate a retry scenario
		final Client client = (request, options) -> {
			// we simulate an exception only for the first request
			if (atomicInteger.get() == 1) {
				throw new IOException();
			}
			else {
				// with the second retry (first retry) we send back good result
				return Response.builder().status(200).reason("OK").headers(new HashMap<>())
						.body("OK", Charset.defaultCharset()).request(Request.create(Request.HttpMethod.POST, "/foo",
								new HashMap<>(), Request.Body.empty(), new RequestTemplate()))
						.build();
			}
		};
		TestInterface api = Feign.builder().client(new TracingFeignClient(tracerTest().tracing().currentTraceContext(),
				tracerTest().tracing().httpClientHandler(), (request, options) -> {
					atomicInteger.incrementAndGet();
					return client.execute(request, options);
				})).target(TestInterface.class, url);

		BDDAssertions.then(api.decodedPost()).isEqualTo("OK");
		// request interception should take place only twice (1st request & 2nd retry)
		BDDAssertions.then(atomicInteger.get()).isEqualTo(2);
		assertException();
		BDDAssertions.then(tracerTest().handler().reportedSpans().get(1).getKind()).isEqualTo(Span.Kind.CLIENT);
	}

	public void assertException() {
		throw new UnsupportedOperationException("Implement this assertion");
	}

	interface TestInterface {

		@RequestLine("POST /")
		String decodedPost();

	}

}
