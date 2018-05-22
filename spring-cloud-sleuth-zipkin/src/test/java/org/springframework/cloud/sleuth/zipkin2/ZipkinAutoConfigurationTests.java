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
import org.springframework.cloud.sleuth.SpanAdjuster;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.reporter.Sender;
import zipkin2.reporter.amqp.RabbitMQSender;
import zipkin2.reporter.kafka11.KafkaSender;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

/**
 * Not using {@linkplain SpringBootTest} as we need to change properties per test.
 */
public class ZipkinAutoConfigurationTests {

	@Rule public ExpectedException thrown = ExpectedException.none();
	@Rule public MockWebServer server = new MockWebServer();

	AnnotationConfigApplicationContext context;

	@After
	public void close() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void defaultsToV2Endpoint() throws Exception {
		context = new AnnotationConfigApplicationContext();
		addEnvironment(context, "spring.zipkin.base-url:" + server.url("/"));
		context.register(
				ZipkinAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class,
				TraceAutoConfiguration.class,
				Config.class);
		context.refresh();
		Span span =
				context.getBean(Tracing.class).tracer().nextSpan()
						.name("foo").tag("foo", "bar")
						.start();

		span.finish();

		Awaitility.await().untilAsserted(() -> then(server.getRequestCount()).isGreaterThan(0));
		RecordedRequest request = server.takeRequest();
		then(request.getPath()).isEqualTo("/api/v2/spans");
		then(request.getBody().readUtf8()).contains("localEndpoint");
	}

	@Test
	public void encoderDirectsEndpoint() throws Exception {
		context = new AnnotationConfigApplicationContext();
		addEnvironment(
				context, "spring.zipkin.base-url:" + server.url("/"), "spring.zipkin.encoder:JSON_V1");
		context.register(
				ZipkinAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class,
				TraceAutoConfiguration.class,
				Config.class);
		context.refresh();
		Span span =
				context.getBean(Tracing.class).tracer().nextSpan()
						.name("foo").tag("foo", "bar")
						.start();

		span.finish();

		Awaitility.await().untilAsserted(() -> then(server.getRequestCount()).isGreaterThan(0));
		RecordedRequest request = server.takeRequest();
		then(request.getPath()).isEqualTo("/api/v1/spans");
		then(request.getBody().readUtf8()).contains("binaryAnnotations");
	}

	@Test
	public void overrideRabbitMQQueue() throws Exception {
		context = new AnnotationConfigApplicationContext();
		addEnvironment(context, "spring.zipkin.rabbitmq.queue:zipkin2");
		context.register(
				PropertyPlaceholderAutoConfiguration.class,
				RabbitAutoConfiguration.class,
				ZipkinAutoConfiguration.class);
		context.refresh();

		then(context.getBean(Sender.class)).isInstanceOf(RabbitMQSender.class);

		context.close();
	}

	@Test
	public void overrideKafkaTopic() throws Exception {
		context = new AnnotationConfigApplicationContext();
		addEnvironment(context, "spring.zipkin.kafka.topic:zipkin2",
				"spring.zipkin.kafka.enabled:true");
		context.register(
				PropertyPlaceholderAutoConfiguration.class,
				KafkaAutoConfiguration.class,
				ZipkinAutoConfiguration.class);
		context.refresh();

		then(context.getBean(Sender.class)).isInstanceOf(KafkaSender.class);

		context.close();
	}

	@Test
	public void canOverrideBySender() throws Exception {
		context = new AnnotationConfigApplicationContext();
		addEnvironment(context, "spring.zipkin.sender.type:web");
		context.register(
				PropertyPlaceholderAutoConfiguration.class,
				RabbitAutoConfiguration.class,
				KafkaAutoConfiguration.class,
				ZipkinAutoConfiguration.class);
		context.refresh();

		then(context.getBean(Sender.class).getClass().getName()).contains("RestTemplateSender");

		context.close();
	}

	@Test
	public void canOverrideBySenderAndIsCaseInsensitive() throws Exception {
		context = new AnnotationConfigApplicationContext();
		addEnvironment(context, "spring.zipkin.sender.type:WEB");
		context.register(
				PropertyPlaceholderAutoConfiguration.class,
				RabbitAutoConfiguration.class,
				KafkaAutoConfiguration.class,
				ZipkinAutoConfiguration.class);
		context.refresh();

		then(context.getBean(Sender.class).getClass().getName()).contains("RestTemplateSender");

		context.close();
	}

	@Test
	public void rabbitWinsWhenKafkaPresent() throws Exception {
		context = new AnnotationConfigApplicationContext();
		context.register(
				PropertyPlaceholderAutoConfiguration.class,
				RabbitAutoConfiguration.class,
				KafkaAutoConfiguration.class,
				ZipkinAutoConfiguration.class);
		context.refresh();

		then(context.getBean(Sender.class)).isInstanceOf(RabbitMQSender.class);

		context.close();
	}

	@Configuration
	protected static class Config {
		@Bean Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}
	}

	@Configuration
	protected static class AdjustersConfig {
		@Bean SpanAdjuster adjusterOne() {
			return span -> span.toBuilder().name("foo").build();
		}

		@Bean SpanAdjuster adjusterTwo() {
			return span -> span.toBuilder().name(span.name() + " bar").build();
		}
	}
}
