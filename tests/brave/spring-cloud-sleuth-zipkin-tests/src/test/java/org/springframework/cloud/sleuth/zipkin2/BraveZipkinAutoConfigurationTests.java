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

package org.springframework.cloud.sleuth.zipkin2;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.brave.ZipkinSpanHandler;

import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.brave.autoconfig.BraveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.springframework.cloud.sleuth.zipkin2.ZipkinBraveConfiguration.SPAN_HANDLER_COMPARATOR;

public class BraveZipkinAutoConfigurationTests
		extends org.springframework.cloud.sleuth.zipkin2.ZipkinAutoConfigurationTests {

	@Override
	protected Class tracerZipkinConfiguration() {
		return ZipkinBraveConfiguration.class;
	}

	@Override
	protected Class tracerConfiguration() {
		return BraveAutoConfiguration.class;
	}

	@Override
	protected Class configurationClass() {
		return Config.class;
	}

	@Test
	public void supportsMultipleReporters() throws Exception {
		zipkinRunner().withUserConfiguration(MultipleReportersConfig.class)
				.withPropertyValues("spring.zipkin.base-url=" + this.server.url("/").toString()).run(context -> {
					then(context.getBeansOfType(Sender.class)).hasSize(2);
					then(context.getBeansOfType(Sender.class)).containsKeys(ZipkinAutoConfiguration.SENDER_BEAN_NAME,
							"otherSender");

					then(context.getBeansOfType(Reporter.class)).hasSize(2);
					then(context.getBeansOfType(Reporter.class))
							.containsKeys(ZipkinAutoConfiguration.REPORTER_BEAN_NAME, "otherReporter");

					context.getBean(Tracer.class).nextSpan().name("foo").tag("foo", "bar").start().end();

					context.getBean(ZipkinAutoConfiguration.REPORTER_BEAN_NAME, AsyncReporter.class).flush();
					Awaitility.await().atMost(250, TimeUnit.MILLISECONDS)
							.untilAsserted(() -> then(this.server.getRequestCount()).isGreaterThan(1));

					Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
						RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);
						then(request.getPath()).isEqualTo("/api/v2/spans");
						then(request.getBody().readUtf8()).contains("localEndpoint");
					});

					MultipleReportersConfig.OtherSender sender = context
							.getBean(MultipleReportersConfig.OtherSender.class);
					Awaitility.await().atMost(250, TimeUnit.MILLISECONDS)
							.untilAsserted(() -> then(sender.isSpanSent()).isTrue());
				});
	}

	@Test
	void span_handler_comparator() {
		SpanHandler handler1 = mock(SpanHandler.class);
		SpanHandler handler2 = mock(SpanHandler.class);
		ZipkinSpanHandler zipkin1 = mock(ZipkinSpanHandler.class);
		ZipkinSpanHandler zipkin2 = mock(ZipkinSpanHandler.class);

		ArrayList<SpanHandler> spanHandlers = new ArrayList<>();
		spanHandlers.add(handler1);
		spanHandlers.add(zipkin1);
		spanHandlers.add(handler2);
		spanHandlers.add(zipkin2);

		spanHandlers.sort(SPAN_HANDLER_COMPARATOR);

		assertThat(spanHandlers).containsExactly(handler1, handler2, zipkin1, zipkin2);
	}

	@Configuration(proxyBeanMethods = false)
	protected static class Config {

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

	@Configuration(proxyBeanMethods = false)
	protected static class HandlersConfig {

		@Bean
		SpanHandler handlerOne() {
			return new SpanHandler() {
				@Override
				public boolean end(TraceContext traceContext, MutableSpan span, Cause cause) {
					span.name("foo");
					return true; // keep this span
				}
			};
		}

		@Bean
		SpanHandler handlerTwo() {
			return new SpanHandler() {
				@Override
				public boolean end(TraceContext traceContext, MutableSpan span, Cause cause) {
					span.name(span.name() + " bar");
					return true; // keep this span
				}
			};
		}

	}

}
