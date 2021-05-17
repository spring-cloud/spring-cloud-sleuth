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

package org.springframework.cloud.sleuth.instrument.kafka;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.cloud.sleuth.docs.AssertingSpanBuilder;
import org.springframework.cloud.sleuth.propagation.Propagator;

final class KafkaTracingUtils {

	private static final Log log = LogFactory.getLog(KafkaTracingUtils.class);

	private KafkaTracingUtils() {
	}

	static <K, V> void buildAndFinishSpan(ConsumerRecord<K, V> consumerRecord, Propagator propagator,
			Propagator.Getter<ConsumerRecord<?, ?>> extractor) {
		// @formatter:off
		Span.Builder spanBuilder = AssertingSpanBuilder.of(SleuthKafkaSpan.KAFKA_CONSUMER_SPAN, propagator.extract(consumerRecord, extractor).kind(Span.Kind.CONSUMER))
				.name(SleuthKafkaSpan.KAFKA_CONSUMER_SPAN.getName())
				.tag(SleuthKafkaSpan.ConsumerTags.TOPIC, consumerRecord.topic())
				.tag(SleuthKafkaSpan.ConsumerTags.OFFSET, Long.toString(consumerRecord.offset()))
				.tag(SleuthKafkaSpan.ConsumerTags.PARTITION, Integer.toString(consumerRecord.partition()));
		// @formatter:on
		Span span = spanBuilder.start();
		if (log.isDebugEnabled()) {
			log.debug("Extracted span from event headers " + span);
		}
		span.end();
	}

}
