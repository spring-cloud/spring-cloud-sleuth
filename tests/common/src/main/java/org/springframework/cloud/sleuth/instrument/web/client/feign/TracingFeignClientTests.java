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
import java.util.HashMap;

import feign.Client;
import feign.Request;
import feign.RequestTemplate;
import org.assertj.core.api.BDDAssertions;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;

/**
 * @author Marcin Grzejszczak
 * @author Hash.Jang
 */
@ExtendWith(MockitoExtension.class)
public abstract class TracingFeignClientTests implements TestTracingAwareSupplier {

	RequestTemplate requestTemplate = new RequestTemplate();

	Request request = Request.create(Request.HttpMethod.GET, "https://foo", new HashMap<>(), null, null,
			requestTemplate);

	Request.Options options = new Request.Options();

	@Mock
	Client client;

	Client traceFeignClient;

	@BeforeEach
	public void setup() {
		this.traceFeignClient = TracingFeignClient.create(tracerTest().tracing().currentTraceContext(),
				tracerTest().tracing().httpClientHandler(), this.client);
	}

	@Test
	public void should_log_cr_when_response_successful() throws IOException {
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceFeignClient.execute(this.request, this.options);
		}
		finally {
			span.end();
		}

		BDDAssertions.then(tracerTest().handler().reportedSpans().get(0).getKind()).isEqualTo(Span.Kind.CLIENT);
	}

	@Test
	public void should_log_error_when_exception_thrown() throws IOException {
		RuntimeException error = new RuntimeException("exception has occurred");
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");
		BDDMockito.given(this.client.execute(BDDMockito.any(), BDDMockito.any())).willThrow(error);

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceFeignClient.execute(this.request, this.options);
			BDDAssertions.fail("Exception should have been thrown");
		}
		catch (Exception e) {
		}
		finally {
			span.end();
		}

		BDDAssertions.then(this.tracerTest().handler().reportedSpans().get(0).getKind()).isEqualTo(Span.Kind.CLIENT);
		assertException(error);
	}

	public void assertException(RuntimeException error) {
		throw new UnsupportedOperationException("Implement this assertion");
	}

	@Test
	public void keep_requestTemplate() throws IOException {
		BDDMockito.given(this.client.execute(BDDMockito.any(), BDDMockito.any())).willAnswer(new Answer() {
			public Object answer(InvocationOnMock invocation) {
				Object[] args = invocation.getArguments();
				Assert.assertEquals(((Request) args[0]).requestTemplate(), requestTemplate);
				return null;
			}
		});
		this.traceFeignClient.execute(this.request, this.options);
	}

}
