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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;

import static org.awaitility.Awaitility.await;

@Testcontainers
public abstract class KafkaConsumerTest implements TestTracingAwareSupplier {

	protected String testTopic;

	protected Tracer tracer = tracerTest().tracing().tracer();

	protected Propagator propagator = tracerTest().tracing().propagator();

	protected TestSpanHandler spans = tracerTest().handler();

	protected TracingKafkaConsumer<String, String> kafkaConsumer;

	private final AtomicBoolean consumerRun = new AtomicBoolean();

	protected final AtomicInteger receivedCounter = new AtomicInteger(0);

	@Container
	protected static final KafkaContainer kafkaContainer = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:5.2.1")).withExposedPorts(9093)
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
		testTopic = UUID.randomUUID().toString();
		Map<String, Object> consumerProperties = new HashMap<>();
		consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
		consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
		consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		kafkaConsumer = new TracingKafkaConsumer<>(new KafkaConsumer<>(consumerProperties), propagator,
				new TracingKafkaPropagatorGetter());
		consumerRun.set(true);
		Executors.newSingleThreadExecutor().execute(() -> doStartKafkaConsumer(receivedCounter));
	}

	@AfterEach
	void destroy() {
		consumerRun.set(false);
	}

	@Test
	public void should_create_and_finish_consumer_span() {
		KafkaProducer<String, String> kafkaProducer = KafkaTestUtils
				.buildTestKafkaProducer(kafkaContainer.getBootstrapServers());
		ProducerRecord<String, String> producerRecord = new ProducerRecord<>(testTopic, "test", "test");
		kafkaProducer.send(producerRecord);
		kafkaProducer.close();

		await().atMost(Duration.ofSeconds(5)).until(() -> receivedCounter.intValue() == 1);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).hasSize(1);
		FinishedSpan span = this.spans.get(0);
		BDDAssertions.then(span.getKind()).isEqualTo(Span.Kind.CONSUMER);
		BDDAssertions.then(span.getTags()).isNotEmpty();
		BDDAssertions.then(span.getTags().get("kafka.topic")).isEqualTo(testTopic);
		BDDAssertions.then(span.getTags().get("kafka.offset")).isEqualTo("0");
		BDDAssertions.then(span.getTags().get("kafka.partition")).isEqualTo("0");
	}

	private void doStartKafkaConsumer(AtomicInteger receivedCounter) {
		this.kafkaConsumer.subscribe(Pattern.compile(this.testTopic));
		while (this.consumerRun.get()) {
			ConsumerRecords<String, String> records = this.kafkaConsumer.poll(Duration.ofSeconds(1));
			for (ConsumerRecord<String, String> record : records) {
				receivedCounter.incrementAndGet();
			}
		}
		this.kafkaConsumer.close();
	}

	@Override
	public void cleanUpTracing() {
		this.spans.clear();
	}

}
