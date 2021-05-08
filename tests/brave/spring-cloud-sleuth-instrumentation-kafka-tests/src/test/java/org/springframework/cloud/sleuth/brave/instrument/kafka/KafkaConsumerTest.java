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

package org.springframework.cloud.sleuth.brave.instrument.kafka;

import java.time.Duration;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.brave.BraveTestTracing;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.instrument.kafka.KafkaTestUtils;
import org.springframework.cloud.sleuth.test.TestTracingAware;

import static org.awaitility.Awaitility.await;

public class KafkaConsumerTest extends org.springframework.cloud.sleuth.instrument.kafka.KafkaConsumerTest {

	BraveTestTracing testTracing;

	@Override
	public TestTracingAware tracerTest() {
		if (this.testTracing == null) {
			this.testTracing = new BraveTestTracing();
		}
		return this.testTracing;
	}

	@Test
	public void should_consider_native_headers() {
		KafkaProducer<String, String> kafkaProducer = KafkaTestUtils
				.buildTestKafkaProducer(kafkaContainer.getBootstrapServers());
		ProducerRecord<String, String> producerRecord = new ProducerRecord<>(testTopic, "test", "test");
		producerRecord.headers().add("b3", "000000000000000a-000000000000000b-1-000000000000000a".getBytes());
		kafkaProducer.send(producerRecord);
		kafkaProducer.close();

		await().atMost(Duration.ofSeconds(15)).until(() -> receivedCounter.intValue() == 1);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).hasSize(1);
		FinishedSpan span = this.spans.get(0);
		BDDAssertions.then(span.getKind()).isEqualTo(Span.Kind.CONSUMER);
		BDDAssertions.then(span.getTraceId()).isEqualTo("000000000000000a");
		BDDAssertions.then(span.getParentId()).isEqualTo("000000000000000b");
	}

}
