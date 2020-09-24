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

package org.springframework.cloud.sleuth.brave.instrument.web.client.feign;

import java.io.IOException;
import java.util.HashMap;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.test.TestSpanHandler;
import feign.Client;
import feign.Request;
import feign.RequestTemplate;
import org.assertj.core.api.BDDAssertions;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 * @author Hash.Jang
 */
@ExtendWith(MockitoExtension.class)
public class TracingFeignClientTests {

	RequestTemplate requestTemplate = new RequestTemplate();

	Request request = Request.create(Request.HttpMethod.GET, "https://foo", new HashMap<>(), null, null,
			requestTemplate);

	Request.Options options = new Request.Options();

	StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();

	TestSpanHandler spans = new TestSpanHandler();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(currentTraceContext).addSpanHandler(spans).build();

	Tracer tracer = this.tracing.tracer();

	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing).build();

	@Mock
	Client client;

	Client traceFeignClient;

	@BeforeEach
	public void setup() {
		this.traceFeignClient = TracingFeignClient.create(this.httpTracing, this.client);
	}

	@AfterEach
	public void close() {
		this.tracing.close();
		this.currentTraceContext.close();
	}

	@Test
	public void should_log_cr_when_response_successful() throws IOException {
		Span span = this.tracer.nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.traceFeignClient.execute(this.request, this.options);
		}
		finally {
			span.finish();
		}

		then(spans.get(0).kind()).isEqualTo(Span.Kind.CLIENT);
	}

	@Test
	public void should_log_error_when_exception_thrown() throws IOException {
		RuntimeException error = new RuntimeException("exception has occurred");
		Span span = this.tracer.nextSpan().name("foo");
		BDDMockito.given(this.client.execute(BDDMockito.any(), BDDMockito.any())).willThrow(error);

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.traceFeignClient.execute(this.request, this.options);
			BDDAssertions.fail("Exception should have been thrown");
		}
		catch (Exception e) {
		}
		finally {
			span.finish();
		}

		then(this.spans.get(0).kind()).isEqualTo(Span.Kind.CLIENT);
		then(this.spans.get(0).error()).isSameAs(error);
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
