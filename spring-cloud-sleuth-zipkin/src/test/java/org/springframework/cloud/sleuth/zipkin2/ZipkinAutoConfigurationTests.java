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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brave.Span;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.codec.Encoding;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.Sender;
import zipkin2.reporter.activemq.ActiveMQSender;
import zipkin2.reporter.amqp.RabbitMQSender;
import zipkin2.reporter.brave.ZipkinSpanHandler;
import zipkin2.reporter.kafka.KafkaSender;
import zipkin2.reporter.metrics.micrometer.MicrometerReporterMetrics;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.brave.autoconfig.TraceBraveAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.sleuth.zipkin2.ZipkinBraveAutoConfiguration.SPAN_HANDLER_COMPARATOR;

/**
 * Not using {@linkplain SpringBootTest} as we need to change properties per test.
 *
 * @author Adrian Cole
 */
public class ZipkinAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(ZipkinAutoConfiguration.class, ZipkinBraveAutoConfiguration.class));

	public MockWebServer server = new MockWebServer();

	MockEnvironment environment = new MockEnvironment();

	AnnotationConfigApplicationContext context;

	@BeforeEach
	void setup() throws IOException {
		server.start();
	}

	@AfterEach
	void clean() throws IOException {
		server.close();
	}

	@AfterEach
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
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

	@Test
	void should_apply_micrometer_reporter_metrics_when_meter_registry_bean_present() {
		this.contextRunner.withUserConfiguration(WithMeterRegistry.class).run((context) -> {
			ReporterMetrics bean = context.getBean(ReporterMetrics.class);

			BDDAssertions.then(bean).isInstanceOf(MicrometerReporterMetrics.class);
		});
	}

	@Test
	void should_apply_in_memory_metrics_when_meter_registry_bean_missing() {
		this.contextRunner.run((context) -> {
			ReporterMetrics bean = context.getBean(ReporterMetrics.class);

			BDDAssertions.then(bean).isInstanceOf(InMemoryReporterMetrics.class);
		});
	}

	@Test
	void should_apply_in_memory_metrics_when_meter_registry_class_missing() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(MeterRegistry.class)).run((context) -> {
			ReporterMetrics bean = context.getBean(ReporterMetrics.class);

			BDDAssertions.then(bean).isInstanceOf(InMemoryReporterMetrics.class);
		});
	}

	@Test
	void defaultsToV2Endpoint() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.base-url", this.server.url("/").toString());
		this.context.register(ZipkinAutoConfiguration.class, ZipkinBraveAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class, TraceAutoConfiguration.class,
				TraceBraveAutoConfiguration.class, Config.class);
		this.context.refresh();
		Span span = this.context.getBean(Tracing.class).tracer().nextSpan().name("foo").tag("foo", "bar").start();

		span.finish();

		this.context.getBean(ZipkinAutoConfiguration.REPORTER_BEAN_NAME, AsyncReporter.class).flush();
		Awaitility.await().atMost(250, TimeUnit.MILLISECONDS)
				.untilAsserted(() -> then(this.server.getRequestCount()).isGreaterThan(1));

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);
			then(request.getPath()).isEqualTo("/api/v2/spans");
			then(request.getBody().readUtf8()).contains("localEndpoint");
		});
	}

	private MockEnvironment environment() {
		this.context.setEnvironment(this.environment);
		return this.environment;
	}

	@Test
	public void encoderDirectsEndpoint() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.base-url", this.server.url("/").toString());
		environment().setProperty("spring.zipkin.encoder", "JSON_V1");
		this.context.register(ZipkinAutoConfiguration.class, ZipkinBraveAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class, TraceAutoConfiguration.class,
				TraceBraveAutoConfiguration.class, Config.class);
		this.context.refresh();
		Span span = this.context.getBean(Tracing.class).tracer().nextSpan().name("foo").tag("foo", "bar").start();

		span.finish();

		Awaitility.await().atMost(250, TimeUnit.MILLISECONDS)
				.untilAsserted(() -> then(this.server.getRequestCount()).isGreaterThan(0));

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);
			then(request.getPath()).isEqualTo("/api/v1/spans");
			then(request.getBody().readUtf8()).contains("binaryAnnotations");
		});
	}

	@Test
	public void overrideRabbitMQQueue() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.rabbitmq.queue", "zipkin2");
		environment().setProperty("spring.zipkin.sender.type", "rabbit");
		this.context.register(PropertyPlaceholderAutoConfiguration.class, RabbitAutoConfiguration.class,
				ZipkinAutoConfiguration.class, TraceAutoConfiguration.class);
		this.context.refresh();

		then(this.context.getBean(Sender.class)).isInstanceOf(RabbitMQSender.class);

		this.context.close();
	}

	@Test
	public void overrideKafkaTopic() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.kafka.topic", "zipkin2");
		environment().setProperty("spring.zipkin.sender.type", "kafka");
		this.context.register(PropertyPlaceholderAutoConfiguration.class, KafkaAutoConfiguration.class,
				ZipkinAutoConfiguration.class, TraceAutoConfiguration.class);
		this.context.refresh();

		then(this.context.getBean(Sender.class)).isInstanceOf(KafkaSender.class);

		this.context.close();
	}

	@Test
	public void overrideActiveMqQueue() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.jms.cache.enabled", "false");
		environment().setProperty("spring.zipkin.activemq.queue", "zipkin2");
		environment().setProperty("spring.zipkin.activemq.message-max-bytes", "50");
		environment().setProperty("spring.zipkin.sender.type", "activemq");
		this.context.register(PropertyPlaceholderAutoConfiguration.class, ActiveMQAutoConfiguration.class,
				ZipkinAutoConfiguration.class, TraceAutoConfiguration.class);
		this.context.refresh();

		then(this.context.getBean(Sender.class)).isInstanceOf(ActiveMQSender.class);

		this.context.close();
	}

	@Test
	public void canOverrideBySender() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.sender.type", "web");
		this.context.register(PropertyPlaceholderAutoConfiguration.class, RabbitAutoConfiguration.class,
				KafkaAutoConfiguration.class, ZipkinAutoConfiguration.class, TraceAutoConfiguration.class);
		this.context.refresh();

		then(this.context.getBean(Sender.class).getClass().getName()).contains("RestTemplateSender");

		this.context.close();
	}

	@Test
	public void canOverrideBySenderAndIsCaseInsensitive() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.sender.type", "WEB");
		this.context.register(PropertyPlaceholderAutoConfiguration.class, RabbitAutoConfiguration.class,
				KafkaAutoConfiguration.class, ZipkinAutoConfiguration.class, TraceAutoConfiguration.class);
		this.context.refresh();

		then(this.context.getBean(Sender.class).getClass().getName()).contains("RestTemplateSender");

		this.context.close();
	}

	@Test
	public void rabbitWinsWhenKafkaPresent() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.sender.type", "rabbit");
		this.context.register(PropertyPlaceholderAutoConfiguration.class, RabbitAutoConfiguration.class,
				KafkaAutoConfiguration.class, ZipkinAutoConfiguration.class, TraceAutoConfiguration.class);
		this.context.refresh();

		then(this.context.getBean(Sender.class)).isInstanceOf(RabbitMQSender.class);

		this.context.close();
	}

	@Test
	public void supportsMultipleReporters() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.base-url", this.server.url("/").toString());
		this.context.register(ZipkinAutoConfiguration.class, ZipkinBraveAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class, TraceAutoConfiguration.class,
				TraceBraveAutoConfiguration.class, Config.class, MultipleReportersConfig.class);
		this.context.refresh();

		then(this.context.getBeansOfType(Sender.class)).hasSize(2);
		then(this.context.getBeansOfType(Sender.class)).containsKeys(ZipkinAutoConfiguration.SENDER_BEAN_NAME,
				"otherSender");

		then(this.context.getBeansOfType(Reporter.class)).hasSize(2);
		then(this.context.getBeansOfType(Reporter.class)).containsKeys(ZipkinAutoConfiguration.REPORTER_BEAN_NAME,
				"otherReporter");

		Span span = this.context.getBean(Tracing.class).tracer().nextSpan().name("foo").tag("foo", "bar").start();

		span.finish();

		this.context.getBean(ZipkinAutoConfiguration.REPORTER_BEAN_NAME, AsyncReporter.class).flush();
		Awaitility.await().atMost(250, TimeUnit.MILLISECONDS)
				.untilAsserted(() -> then(this.server.getRequestCount()).isGreaterThan(1));

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);
			then(request.getPath()).isEqualTo("/api/v2/spans");
			then(request.getBody().readUtf8()).contains("localEndpoint");
		});

		MultipleReportersConfig.OtherSender sender = this.context.getBean(MultipleReportersConfig.OtherSender.class);
		Awaitility.await().atMost(250, TimeUnit.MILLISECONDS).untilAsserted(() -> then(sender.isSpanSent()).isTrue());
	}

	@Test
	public void shouldOverrideDefaultBeans() {
		this.context = new AnnotationConfigApplicationContext();
		this.context.register(ZipkinAutoConfiguration.class, ZipkinBraveAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class, TraceAutoConfiguration.class,
				TraceBraveAutoConfiguration.class, Config.class, MyConfig.class);
		this.context.refresh();

		then(this.context.getBeansOfType(Sender.class)).hasSize(1);
		then(this.context.getBeansOfType(Sender.class)).containsKeys(ZipkinAutoConfiguration.SENDER_BEAN_NAME);

		then(this.context.getBeansOfType(Reporter.class)).hasSize(1);
		then(this.context.getBeansOfType(Reporter.class)).containsKeys(ZipkinAutoConfiguration.REPORTER_BEAN_NAME);

		Span span = this.context.getBean(Tracing.class).tracer().nextSpan().name("foo").tag("foo", "bar").start();

		span.finish();

		Awaitility.await().atMost(250, TimeUnit.MILLISECONDS)
				.untilAsserted(() -> then(this.server.getRequestCount()).isEqualTo(0));

		this.context.getBean(ZipkinAutoConfiguration.REPORTER_BEAN_NAME, AsyncReporter.class).flush();
		MyConfig.MySender sender = this.context.getBean(MyConfig.MySender.class);
		Awaitility.await().atMost(250, TimeUnit.MILLISECONDS).untilAsserted(() -> then(sender.isSpanSent()).isTrue());
	}

	@Test
	public void checkResult_onTime() {
		Sender sender = mock(Sender.class);
		when(sender.check()).thenReturn(CheckResult.OK);

		assertThat(ZipkinAutoConfiguration.checkResult(sender, 200).ok()).isTrue();
	}

	@Test
	public void checkResult_onTime_notOk() {
		Sender sender = mock(Sender.class);
		RuntimeException exception = new RuntimeException("dead");
		when(sender.check()).thenReturn(CheckResult.failed(exception));

		assertThat(ZipkinAutoConfiguration.checkResult(sender, 200).error()).isSameAs(exception);
	}

	/** Bug in {@link Sender} as it shouldn't throw */
	@Test
	public void checkResult_thrown() {
		Sender sender = mock(Sender.class);
		RuntimeException exception = new RuntimeException("dead");
		when(sender.check()).thenThrow(exception);

		assertThat(ZipkinAutoConfiguration.checkResult(sender, 200).error()).isSameAs(exception);
	}

	@Test
	public void checkResult_slow() {
		assertThat(ZipkinAutoConfiguration.checkResult(new Sender() {
			@Override
			public CheckResult check() {
				try {
					Thread.sleep(500L);
				}
				catch (InterruptedException e) {
					throw new AssertionError(e);
				}
				return CheckResult.OK;
			}

			@Override
			public Encoding encoding() {
				return Encoding.JSON;
			}

			@Override
			public int messageMaxBytes() {
				return 0;
			}

			@Override
			public int messageSizeInBytes(List<byte[]> list) {
				return 0;
			}

			@Override
			public Call<Void> sendSpans(List<byte[]> list) {
				return Call.create(null);
			}

			@Override
			public String toString() {
				return "FakeSender{}";
			}
		}, 200).error()).isInstanceOf(TimeoutException.class).hasMessage("FakeSender{} check() timed out after 200ms");
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

	@Configuration(proxyBeanMethods = false)
	static class WithMeterRegistry {

		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class WithReporter {

		@Bean
		Reporter<zipkin2.Span> spanReporter() {
			return zipkin2.Span::toString;
		}

	}

	@Configuration(proxyBeanMethods = false)
	protected static class MultipleReportersConfig {

		@Bean
		Reporter<zipkin2.Span> otherReporter(OtherSender otherSender) {
			return AsyncReporter.create(otherSender);
		}

		@Bean
		OtherSender otherSender() {
			return new OtherSender();
		}

		static class OtherSender extends Sender {

			private boolean spanSent = false;

			boolean isSpanSent() {
				return this.spanSent;
			}

			@Override
			public Encoding encoding() {
				return Encoding.JSON;
			}

			@Override
			public int messageMaxBytes() {
				return Integer.MAX_VALUE;
			}

			@Override
			public int messageSizeInBytes(List<byte[]> encodedSpans) {
				return encoding().listSizeInBytes(encodedSpans);
			}

			@Override
			public Call<Void> sendSpans(List<byte[]> encodedSpans) {
				this.spanSent = true;
				return Call.create(null);
			}

		}

	}

	// tag::override_default_beans[]

	@Configuration(proxyBeanMethods = false)
	protected static class MyConfig {

		@Bean(ZipkinAutoConfiguration.REPORTER_BEAN_NAME)
		Reporter<zipkin2.Span> myReporter(@Qualifier(ZipkinAutoConfiguration.SENDER_BEAN_NAME) MySender mySender) {
			return AsyncReporter.create(mySender);
		}

		@Bean(ZipkinAutoConfiguration.SENDER_BEAN_NAME)
		MySender mySender() {
			return new MySender();
		}

		static class MySender extends Sender {

			private boolean spanSent = false;

			boolean isSpanSent() {
				return this.spanSent;
			}

			@Override
			public Encoding encoding() {
				return Encoding.JSON;
			}

			@Override
			public int messageMaxBytes() {
				return Integer.MAX_VALUE;
			}

			@Override
			public int messageSizeInBytes(List<byte[]> encodedSpans) {
				return encoding().listSizeInBytes(encodedSpans);
			}

			@Override
			public Call<Void> sendSpans(List<byte[]> encodedSpans) {
				this.spanSent = true;
				return Call.create(null);
			}

		}

	}

	// end::override_default_beans[]

}
