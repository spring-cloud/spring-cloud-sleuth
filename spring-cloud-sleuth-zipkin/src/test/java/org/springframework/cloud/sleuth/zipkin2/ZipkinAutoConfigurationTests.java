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

package org.springframework.cloud.sleuth.zipkin2;

import brave.Span;
import brave.Tracing;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import zipkin2.reporter.Sender;
import zipkin2.reporter.amqp.RabbitMQSender;
import zipkin2.reporter.kafka11.KafkaSender;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Not using {@linkplain SpringBootTest} as we need to change properties per test.
 */
public class ZipkinAutoConfigurationTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Rule
	public MockWebServer server = new MockWebServer();

	MockEnvironment environment = new MockEnvironment();

	AnnotationConfigApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void defaultsToV2Endpoint() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.base-url",
				this.server.url("/").toString());
		this.context.register(ZipkinAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class, TraceAutoConfiguration.class,
				Config.class);
		this.context.refresh();
		Span span = this.context.getBean(Tracing.class).tracer().nextSpan().name("foo")
				.tag("foo", "bar").start();

		span.finish();

		Awaitility.await().untilAsserted(
				() -> then(this.server.getRequestCount()).isGreaterThan(0));
		RecordedRequest request = this.server.takeRequest();
		then(request.getPath()).isEqualTo("/api/v2/spans");
		then(request.getBody().readUtf8()).contains("localEndpoint");
	}

	private MockEnvironment environment() {
		this.context.setEnvironment(this.environment);
		return this.environment;
	}

	@Test
	public void encoderDirectsEndpoint() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.base-url",
				this.server.url("/").toString());
		environment().setProperty("spring.zipkin.encoder", "JSON_V1");
		this.context.register(ZipkinAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class, TraceAutoConfiguration.class,
				Config.class);
		this.context.refresh();
		Span span = this.context.getBean(Tracing.class).tracer().nextSpan().name("foo")
				.tag("foo", "bar").start();

		span.finish();

		Awaitility.await().untilAsserted(
				() -> then(this.server.getRequestCount()).isGreaterThan(0));
		RecordedRequest request = this.server.takeRequest();
		then(request.getPath()).isEqualTo("/api/v1/spans");
		then(request.getBody().readUtf8()).contains("binaryAnnotations");
	}

	@Test
	public void overrideRabbitMQQueue() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.rabbitmq.queue", "zipkin2");
		this.context.register(PropertyPlaceholderAutoConfiguration.class,
				RabbitAutoConfiguration.class, ZipkinAutoConfiguration.class);
		this.context.refresh();

		then(this.context.getBean(Sender.class)).isInstanceOf(RabbitMQSender.class);

		this.context.close();
	}

	@Test
	public void overrideKafkaTopic() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.kafka.topic", "zipkin2");
		environment().setProperty("spring.zipkin.sender.type", "kafka");
		this.context.register(PropertyPlaceholderAutoConfiguration.class,
				KafkaAutoConfiguration.class, ZipkinAutoConfiguration.class);
		this.context.refresh();

		then(this.context.getBean(Sender.class)).isInstanceOf(KafkaSender.class);

		this.context.close();
	}

	@Test
	public void canOverrideBySender() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.sender.type", "web");
		this.context.register(PropertyPlaceholderAutoConfiguration.class,
				RabbitAutoConfiguration.class, KafkaAutoConfiguration.class,
				ZipkinAutoConfiguration.class);
		this.context.refresh();

		then(this.context.getBean(Sender.class).getClass().getName())
				.contains("RestTemplateSender");

		this.context.close();
	}

	@Test
	public void canOverrideBySenderAndIsCaseInsensitive() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.sender.type", "WEB");
		this.context.register(PropertyPlaceholderAutoConfiguration.class,
				RabbitAutoConfiguration.class, KafkaAutoConfiguration.class,
				ZipkinAutoConfiguration.class);
		this.context.refresh();

		then(this.context.getBean(Sender.class).getClass().getName())
				.contains("RestTemplateSender");

		this.context.close();
	}

	@Test
	public void rabbitWinsWhenKafkaPresent() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		this.context.register(PropertyPlaceholderAutoConfiguration.class,
				RabbitAutoConfiguration.class, KafkaAutoConfiguration.class,
				ZipkinAutoConfiguration.class);
		this.context.refresh();

		then(this.context.getBean(Sender.class)).isInstanceOf(RabbitMQSender.class);

		this.context.close();
	}

	@Configuration
	protected static class Config {

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

	@Configuration
	protected static class HandlerHanldersConfig {

		@Bean
		FinishedSpanHandler handlerOne() {
			return new FinishedSpanHandler() {
				@Override
				public boolean handle(TraceContext traceContext, MutableSpan span) {
					span.name("foo");
					return true; // keep this span
				}
			};
		}

		@Bean
		FinishedSpanHandler handlerTwo() {
			return new FinishedSpanHandler() {
				@Override
				public boolean handle(TraceContext traceContext, MutableSpan span) {
					span.name(span.name() + " bar");
					return true; // keep this span
				}
			};
		}

	}

}
