/*
 * Copyright 2013-2017 the original author or authors.
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

import feign.Client;
import feign.Feign;
import feign.FeignException;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import okhttp3.mockwebserver.MockWebServer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.ExceptionMessageErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpSpanInjector;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.instrument.web.ZipkinHttpSpanInjector;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;

import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class FeignRetriesTests {

	@Rule
	public final MockWebServer server = new MockWebServer();

	@Mock BeanFactory beanFactory;

	ArrayListSpanAccumulator spanAccumulator = new ArrayListSpanAccumulator();
	Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(), new DefaultSpanNamer(),
			new NoOpSpanLogger(), this.spanAccumulator, new TraceKeys());

	@Before
	@After
	public void setup() {
		ExceptionUtils.setFail(true);
		TestSpanContextHolder.removeCurrentSpan();
		BDDMockito.given(this.beanFactory.getBean(HttpTraceKeysInjector.class))
				.willReturn(new HttpTraceKeysInjector(this.tracer, new TraceKeys()));
		BDDMockito.given(this.beanFactory.getBean(HttpSpanInjector.class))
				.willReturn(new ZipkinHttpSpanInjector());
		BDDMockito.given(this.beanFactory.getBean(Tracer.class)).willReturn(this.tracer);
		BDDMockito.given(this.beanFactory.getBean(ErrorParser.class)).willReturn(new ExceptionMessageErrorParser());
	}

	@Test
	public void testRetriedWhenExceededNumberOfRetries() throws Exception {
		Client client = (request, options) -> {
			throw new IOException();
		};
		String url = "http://localhost:" + server.getPort();

		TestInterface api =
				Feign.builder()
						.client(new TraceFeignClient(beanFactory, client))
						.target(TestInterface.class, url);

		try {
			api.decodedPost();
			failBecauseExceptionWasNotThrown(FeignException.class);
		} catch (FeignException e) { }

		then(this.tracer.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void testRetriedWhenRequestEventuallyIsSent() throws Exception {
		String url = "http://localhost:" + server.getPort();
		final AtomicInteger atomicInteger = new AtomicInteger();
		// Client to simulate a retry scenario
		Client client = (request, options) -> {
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
						.client(new TraceFeignClient(beanFactory, client) {
							@Override public Response execute(Request request,
									Request.Options options) throws IOException {
								atomicInteger.incrementAndGet();
								return super.execute(request, options);
							}
						})
						.target(TestInterface.class, url);

		then(api.decodedPost()).isEqualTo("OK");
		// request interception should take place only twice (1st request & 2nd retry)
		then(atomicInteger.get()).isEqualTo(2);
		then(this.tracer.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
		then(this.spanAccumulator.getSpans().get(0))
				.hasATag("error", "java.io.IOException");
		then(this.spanAccumulator.getSpans().get(1))
				.hasLoggedAnEvent(Span.CLIENT_RECV);
	}

	interface TestInterface {

		@RequestLine("POST /")
		String decodedPost();
	}


}
