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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;

import static org.awaitility.Awaitility.await;

@Testcontainers
public abstract class KafkaProducerTest implements TestTracingAwareSupplier {

	protected Tracer tracer = tracerTest().tracing().tracer();

	protected Propagator propagator = tracerTest().tracing().propagator();

	protected TestSpanHandler spans = tracerTest().handler();

	protected TracingKafkaProducer<String, String> kafkaProducer;

	@Container
	protected final KafkaContainer kafkaContainer = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:5.2.1")).withExposedPorts(9093)
					.waitingFor(Wait.forListeningPort());

	@BeforeEach
	void setup() {
		kafkaContainer.start();
		Map<String, Object> properties = new HashMap<>();
		properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
		properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		kafkaProducer = new TracingKafkaProducer<>(new KafkaProducer<>(properties), tracer, propagator,
				new TracingKafkaPropagatorSetter());
	}

	@AfterEach
	void destroy() {
		kafkaContainer.stop();
	}

	@Test
	public void should_create_and_finish_producer_span() {
		AtomicBoolean acknowledged = new AtomicBoolean(false);
		Callback callback = (metadata, ex) -> acknowledged.set(true);
		ProducerRecord<String, String> producerRecord = new ProducerRecord<>("spring-cloud-sleuth-otel-topic", "test",
				"test");
		this.kafkaProducer.send(producerRecord, callback);
		await().atMost(Duration.ofSeconds(5)).until(acknowledged::get);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).isNotEmpty();
		BDDAssertions.then(this.spans.get(0).getKind()).isEqualTo(Span.Kind.PRODUCER);
	}

	@Override
	public void cleanUpTracing() {

	}

}
