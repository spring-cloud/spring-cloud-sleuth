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
import org.springframework.cloud.sleuth.brave.autoconfig.BraveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.sleuth.zipkin2.ZipkinBraveConfiguration.SPAN_HANDLER_COMPARATOR;

/**
 * Not using {@linkplain SpringBootTest} as we need to change properties per test.
 *
 * @author Adrian Cole
 */
public class ZipkinAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ZipkinAutoConfiguration.class, ZipkinBraveConfiguration.class));

	public MockWebServer server = new MockWebServer();

	@BeforeEach
	void setup() throws IOException {
		server.start();
	}

	@AfterEach
	void clean() throws IOException {
		server.close();
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
		zipkinBraveRunner().withPropertyValues("spring.zipkin.base-url=" + this.server.url("/").toString())
				.run(context -> {
					Span span = context.getBean(Tracing.class).tracer().nextSpan().name("foo").tag("foo", "bar")
							.start();

					span.finish();

					context.getBean(ZipkinAutoConfiguration.REPORTER_BEAN_NAME, AsyncReporter.class).flush();
					Awaitility.await().atMost(250, TimeUnit.MILLISECONDS)
							.untilAsserted(() -> then(this.server.getRequestCount()).isGreaterThan(1));

					Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
						RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);
						then(request.getPath()).isEqualTo("/api/v2/spans");
						then(request.getBody().readUtf8()).contains("localEndpoint");
					});
				});
	}

	private ApplicationContextRunner zipkinBraveRunner() {
		return new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ZipkinAutoConfiguration.class, ZipkinBraveConfiguration.class,
						PropertyPlaceholderAutoConfiguration.class, BraveAutoConfiguration.class,
						KafkaAutoConfiguration.class, RabbitAutoConfiguration.class, ActiveMQAutoConfiguration.class))
				.withUserConfiguration(Config.class);
	}

	@Test
	public void encoderDirectsEndpoint() throws Exception {
		zipkinBraveRunner().withPropertyValues("spring.zipkin.base-url=" + this.server.url("/").toString(),
				"spring.zipkin.encoder=JSON_V1").run(context -> {
					Span span = context.getBean(Tracing.class).tracer().nextSpan().name("foo").tag("foo", "bar")
							.start();

					span.finish();

					Awaitility.await().atMost(250, TimeUnit.MILLISECONDS)
							.untilAsserted(() -> then(this.server.getRequestCount()).isGreaterThan(0));

					Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
						RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);
						then(request.getPath()).isEqualTo("/api/v1/spans");
						then(request.getBody().readUtf8()).contains("binaryAnnotations");
					});
				});
	}

	@Test
	public void overrideRabbitMQQueue() throws Exception {
		zipkinBraveRunner()
				.withPropertyValues("spring.zipkin.rabbitmq.queue=zipkin2", "spring.zipkin.sender.type=rabbit")
				.run(context -> then(context.getBean(Sender.class)).isInstanceOf(RabbitMQSender.class));
	}

	@Test
	public void overrideKafkaTopic() throws Exception {
		zipkinBraveRunner().withPropertyValues("spring.zipkin.kafka.topic=zipkin2", "spring.zipkin.sender.type=kafka")
				.run(context -> then(context.getBean(Sender.class)).isInstanceOf(KafkaSender.class));
	}

	@Test
	public void overrideActiveMqQueue() throws Exception {
		zipkinBraveRunner()
				.withPropertyValues("spring.jms.cache.enabled=false", "spring.zipkin.activemq.queue=zipkin2",
						"spring.zipkin.activemq.message-max-bytes=50", "spring.zipkin.sender.type=activemq")
				.run(context -> then(context.getBean(Sender.class)).isInstanceOf(ActiveMQSender.class));
	}

	@Test
	public void canOverrideBySender() throws Exception {
		zipkinBraveRunner().withPropertyValues("spring.zipkin.sender.type=web").run(
				context -> then(context.getBean(Sender.class).getClass().getName()).contains("RestTemplateSender"));
	}

	@Test
	public void canOverrideBySenderAndIsCaseInsensitive() throws Exception {
		zipkinBraveRunner().withPropertyValues("spring.zipkin.sender.type=WEB").run(
				context -> then(context.getBean(Sender.class).getClass().getName()).contains("RestTemplateSender"));
	}

	@Test
	public void rabbitWinsWhenKafkaPresent() throws Exception {
		zipkinBraveRunner().withPropertyValues("spring.zipkin.sender.type=rabbit")
				.run(context -> then(context.getBean(Sender.class)).isInstanceOf(RabbitMQSender.class));
	}

	@Test
	public void supportsMultipleReporters() throws Exception {
		zipkinBraveRunner().withUserConfiguration(MultipleReportersConfig.class)
				.withPropertyValues("spring.zipkin.base-url=" + this.server.url("/").toString()).run(context -> {
					then(context.getBeansOfType(Sender.class)).hasSize(2);
					then(context.getBeansOfType(Sender.class)).containsKeys(ZipkinAutoConfiguration.SENDER_BEAN_NAME,
							"otherSender");

					then(context.getBeansOfType(Reporter.class)).hasSize(2);
					then(context.getBeansOfType(Reporter.class))
							.containsKeys(ZipkinAutoConfiguration.REPORTER_BEAN_NAME, "otherReporter");

					Span span = context.getBean(Tracing.class).tracer().nextSpan().name("foo").tag("foo", "bar")
							.start();

					span.finish();

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
	public void shouldOverrideDefaultBeans() {
		zipkinBraveRunner().withUserConfiguration(MyConfig.class).withPropertyValues("spring.zipkin.sender.type=rabbit",
				"spring.zipkin.base-url=" + this.server.url("/").toString()).run(context -> {
					then(context.getBeansOfType(Sender.class)).hasSize(1);
					then(context.getBeansOfType(Sender.class)).containsKeys(ZipkinAutoConfiguration.SENDER_BEAN_NAME);

					then(context.getBeansOfType(Reporter.class)).hasSize(1);
					then(context.getBeansOfType(Reporter.class))
							.containsKeys(ZipkinAutoConfiguration.REPORTER_BEAN_NAME);

					Span span = context.getBean(Tracing.class).tracer().nextSpan().name("foo").tag("foo", "bar")
							.start();

					span.finish();

					Awaitility.await().atMost(250, TimeUnit.MILLISECONDS)
							.untilAsserted(() -> then(this.server.getRequestCount()).isEqualTo(0));

					context.getBean(ZipkinAutoConfiguration.REPORTER_BEAN_NAME, AsyncReporter.class).flush();
					MyConfig.MySender sender = context.getBean(MyConfig.MySender.class);
					Awaitility.await().atMost(250, TimeUnit.MILLISECONDS)
							.untilAsserted(() -> then(sender.isSpanSent()).isTrue());
				});
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
