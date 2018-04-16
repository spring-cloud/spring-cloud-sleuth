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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import feign.Client;
import feign.Feign;
import feign.FeignException;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.instrument.web.SleuthHttpParserAccessor;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class FeignRetriesTests {

	@Rule
	public final MockWebServer server = new MockWebServer();

	@Mock BeanFactory beanFactory;

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(new StrictCurrentTraceContext())
			.spanReporter(this.reporter)
			.build();
	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing)
			.clientParser(SleuthHttpParserAccessor.getClient())
			.build();

	@Before
	@After
	public void setup() {
		BDDMockito.given(this.beanFactory.getBean(HttpTracing.class)).willReturn(this.httpTracing);
	}

	@Test
	public void testRetriedWhenExceededNumberOfRetries() throws Exception {
		Client client = (request, options) -> {
			throw new IOException();
		};
		String url = "http://localhost:" + server.getPort();

		TestInterface api =
				Feign.builder()
						.client(new TracingFeignClient(this.httpTracing, client))
						.target(TestInterface.class, url);

		try {
			api.decodedPost();
			failBecauseExceptionWasNotThrown(FeignException.class);
		} catch (FeignException e) { }
	}

	@Test
	public void testRetriedWhenRequestEventuallyIsSent() throws Exception {
		String url = "http://localhost:" + server.getPort();
		final AtomicInteger atomicInteger = new AtomicInteger();
		// Client to simulate a retry scenario
		final Client client = (request, options) -> {
			// we simulate an exception only for the first request
			if (atomicInteger.get() == 1) {
				throw new IOException();
			} else {
				// with the second retry (first retry) we send back good result
				return Response.builder()
						.status(200)
						.reason("OK")
						.headers(new HashMap<>())
						.body("OK", Charset.defaultCharset())
						.build();
			}
		};
		TestInterface api =
				Feign.builder()
						.client(new TracingFeignClient(this.httpTracing, new Client() {
							@Override public Response execute(Request request,
									Request.Options options) throws IOException {
								atomicInteger.incrementAndGet();
								return client.execute(request, options);
							}
						}))
						.target(TestInterface.class, url);

		then(api.decodedPost()).isEqualTo("OK");
		// request interception should take place only twice (1st request & 2nd retry)
		then(atomicInteger.get()).isEqualTo(2);
		then(this.reporter.getSpans().get(0).tags())
				.containsEntry("error", "IOException");
		then(this.reporter.getSpans().get(1).kind().ordinal())
				.isEqualTo(Span.Kind.CLIENT.ordinal());
	}

	interface TestInterface {

		@RequestLine("POST /")
		String decodedPost();
	}


}
