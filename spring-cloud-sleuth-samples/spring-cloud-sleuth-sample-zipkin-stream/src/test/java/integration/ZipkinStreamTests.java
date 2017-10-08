/*
 * Copyright 2013-2015 the original author or authors.
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
package integration;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Collections;
import java.util.Random;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.Host;
import org.springframework.cloud.sleuth.stream.SleuthSink;
import org.springframework.cloud.sleuth.stream.Spans;
import org.springframework.cloud.sleuth.zipkin.stream.ZipkinMessageListener;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.test.binder.TestSupportBinderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import example.ZipkinStreamServerApplication;
import org.springframework.test.context.junit4.SpringRunner;
import tools.AbstractIntegrationTest;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.internal.V2StorageComponent;
import zipkin.server.ZipkinHttpCollector;
import zipkin.server.ZipkinQueryApiV1;
import zipkin.storage.StorageComponent;
import zipkin2.storage.InMemoryStorage;

@RunWith(SpringRunner.class)
// TODO: Without ZipkinStreamServerApplication.class cause Zipkin is not comp with Boot
@SpringBootTest(classes = { ZipkinStreamTestsConfig.class,
		TestSupportBinderAutoConfiguration.class},
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "server.port=0",
		"management.health.rabbit.enabled=false" })
@ActiveProfiles("test")
public class ZipkinStreamTests extends AbstractIntegrationTest {

	@Value("${local.server.port}")
	private int zipkinServerPort = 9411;
	long traceId = new Random().nextLong();
	long spanId = new Random().nextLong();

	@Autowired
	@Qualifier(SleuthSink.INPUT)
	private MessageChannel input;

	@Before
	public void setup() {
		await().atMost(10, SECONDS)
				.untilAsserted(() -> zipkinServerIsUp().run());
	}

	@Test
	public void should_propagate_spans_to_zipkin() {
		Span span = Span.builder().traceId(this.traceId).spanId(this.spanId).name("http:test").build();
		span.tag(getRequiredBinaryAnnotationName(), "10131");

		this.input.send(messageWithSpan(span));

		await().atMost(5, SECONDS).untilAsserted(() ->
				allSpansWereRegisteredInZipkinWithTraceIdEqualTo(this.traceId)
		);
	}

	private Message<Spans> messageWithSpan(Span span) {
		return MessageBuilder.withPayload(
				new Spans(new Host(getAppName(), "127.0.0.1", 8080), Collections.singletonList(span)))
				.build();
	}

	@Override
	protected int getZipkinServerPort() {
		return this.zipkinServerPort;
	}

	@Override
	protected String getAppName() {
		return "local";
	}

}

// TODO: Zipkin Server is not Boot 2.0 compatible
@Configuration
@SpringBootApplication
@EnableBoot2CompatibleZipkinServer
class ZipkinStreamTestsConfig {

}

//@EnableZipkinStreamServer
@EnableBinding(SleuthSink.class)
@Import({ZipkinMessageListener.class,
		Boot2ZipkinCompatibleConfig.class,
		ZipkinQueryApiV1.class,
		ZipkinHttpCollector.class})
@interface EnableBoot2CompatibleZipkinServer {
}

// CollectorMetrics bean definition from `ZipkinServerConfiguration`
// is not Boot 2.0 compatible
@Configuration
class Boot2ZipkinCompatibleConfig {

	@Bean CollectorMetrics collectorMetrics() {
		CollectorMetrics mock = Mockito.mock(CollectorMetrics.class);
		Mockito.when(mock.forTransport(Mockito.anyString())).thenReturn(mock);
		return mock;
	}

	@Bean
	@ConditionalOnMissingBean(CollectorSampler.class)
	CollectorSampler traceIdSampler(@Value("${zipkin.collector.sample-rate:1.0}") float rate) {
		return CollectorSampler.create(rate);
	}

	/**
	 * This is a special-case configuration if there's no StorageComponent of any kind. In-Mem can
	 * supply both read apis, so we add two beans here.
	 */
	@Configuration
	// "matchIfMissing = true" ensures this is used when there's no configured storage type
	@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "mem", matchIfMissing = true)
	@ConditionalOnMissingBean(StorageComponent.class)
	static class InMemoryConfiguration {
		@Bean StorageComponent storage(
				@Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
				@Value("${zipkin.storage.mem.max-spans:500000}") int maxSpans) {
			return V2StorageComponent.create(InMemoryStorage.newBuilder()
					.strictTraceId(strictTraceId)
					.maxSpanCount(maxSpans)
					.build());
		}

		@Bean InMemoryStorage v2Storage(V2StorageComponent component) {
			return (InMemoryStorage) component.delegate();
		}
	}
}

