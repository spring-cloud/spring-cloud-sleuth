/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.kafka;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

import static org.awaitility.Awaitility.await;

@Testcontainers
@ExtendWith(MockitoExtension.class)
@Tag("DockerRequired")
public abstract class KafkaProducerTest implements TestTracingAwareSupplier {

	protected String testTopic;

	protected Tracer tracer = tracerTest().tracing().tracer();

	protected Propagator propagator = tracerTest().tracing().propagator();

	protected TestSpanHandler spans = tracerTest().handler();

	protected TracingKafkaProducer<String, String> kafkaProducer;

	private final AtomicBoolean consumerRun = new AtomicBoolean();

	protected final BlockingQueue<ConsumerRecord<String, String>> consumerRecords = new LinkedBlockingQueue<>();

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	BeanFactory beanFactory;

	@Container
	protected static final KafkaContainer kafkaContainer = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:6.1.1")).withExposedPorts(9093)
					.waitingFor(Wait.forListeningPort());

	@BeforeAll
	static void setupAll() {
		kafkaContainer.start();
	}

	@AfterAll
	static void destroyAll() {
		kafkaContainer.stop();
	}

	@BeforeEach
	void setup() {
		BDDMockito.given(this.beanFactory.getBean(Tracer.class)).willReturn(this.tracer);
		BDDMockito.given(this.beanFactory.getBean(Propagator.class)).willReturn(this.propagator);
		BDDMockito.given(this.beanFactory.getBeanProvider(ResolvableType.forClassWithGenerics(Propagator.Setter.class,
				ResolvableType.forType(new ParameterizedTypeReference<ProducerRecord<?, ?>>() {
				}))).getIfAvailable()).willReturn(new TracingKafkaPropagatorSetter());
		testTopic = UUID.randomUUID().toString();
		Map<String, Object> producerProperties = new HashMap<>();
		producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
		producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		kafkaProducer = new TracingKafkaProducer<>(new KafkaProducer<>(producerProperties), beanFactory);
		consumerRun.set(true);
		consumerRecords.clear();
	}

	@AfterEach
	void destroy() {
		this.kafkaProducer.close();
		consumerRun.set(false);
	}

	@Test
	public void should_create_and_finish_producer_span() {
		AtomicBoolean acknowledged = new AtomicBoolean(false);
		Callback callback = (metadata, ex) -> acknowledged.set(true);
		ProducerRecord<String, String> producerRecord = new ProducerRecord<>(testTopic, "test", "test");

		this.kafkaProducer.send(producerRecord, callback);
		await().atMost(Duration.ofSeconds(15)).until(acknowledged::get);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).hasSize(1);
		FinishedSpan span = this.spans.get(0);
		BDDAssertions.then(span.getKind()).isEqualTo(Span.Kind.PRODUCER);
		BDDAssertions.then(span.getTags().get("kafka.topic")).isEqualTo(testTopic);
	}

	protected void startKafkaConsumer() {
		Executors.newSingleThreadExecutor().execute(this::doStartKafkaConsumer);
	}

	private void doStartKafkaConsumer() {
		KafkaConsumer<String, String> kafkaConsumer = KafkaTestUtils
				.buildTestKafkaConsumer(kafkaContainer.getBootstrapServers());
		kafkaConsumer.subscribe(Pattern.compile(testTopic));
		while (consumerRun.get()) {
			ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofSeconds(1));
			for (ConsumerRecord<String, String> record : records) {
				consumerRecords.offer(record);
			}
		}
		kafkaConsumer.close();
	}

	@Override
	public void cleanUpTracing() {
		this.spans.clear();
	}

}
