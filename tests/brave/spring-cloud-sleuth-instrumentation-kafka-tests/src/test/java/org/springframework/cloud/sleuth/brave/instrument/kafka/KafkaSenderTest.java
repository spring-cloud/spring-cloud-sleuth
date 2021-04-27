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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import org.springframework.cloud.sleuth.brave.BraveTestTracing;
import org.springframework.cloud.sleuth.test.TestTracingAware;

public class KafkaSenderTest extends org.springframework.cloud.sleuth.instrument.kafka.KafkaSenderTest {

	BraveTestTracing testTracing;

	@Override
	public TestTracingAware tracerTest() {
		if (this.testTracing == null) {
			this.testTracing = new BraveTestTracing();
		}
		return this.testTracing;
	}

	@Test
	public void should_inject_native_headers() throws InterruptedException {
		ProducerRecord<String, String> producerRecord = new ProducerRecord<>(testTopic, "test", "test");
		startKafkaConsumer();

		Flux<SenderResult<Object>> senderResultFlux = this.kafkaSender
				.send(Mono.just(SenderRecord.create(producerRecord, null)));
		StepVerifier.create(senderResultFlux).expectNextCount(1).verifyComplete();
		ConsumerRecord<String, String> consumerRecord = consumerRecords.poll(5, TimeUnit.SECONDS);

		BDDAssertions.then(consumerRecord).isNotNull();
		BDDAssertions.then(getHeaderValueOrNull(consumerRecord, "X-B3-TraceId")).isNotNull();
		BDDAssertions.then(getHeaderValueOrNull(consumerRecord, "X-B3-SpanId")).isNotNull();
		BDDAssertions.then(getHeaderValueOrNull(consumerRecord, "X-B3-Sampled")).isNotNull();
	}

	private static String getHeaderValueOrNull(ConsumerRecord<?, ?> consumerRecord, String header) {
		return Optional.ofNullable(consumerRecord).map(ConsumerRecord::headers)
				.map(headers -> headers.lastHeader(header)).map(Header::value).map(String::new).orElse(null);
	}

}
