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

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthKafkaSpan implements DocumentedSpan {

	/**
	 * Span created on the Kafka consumer side.
	 */
	KAFKA_CONSUMER_SPAN {
		@Override
		public String getName() {
			return "kafka.consume";
		}

		@Override
		public TagKey[] getTagKeys() {
			return ConsumerTags.values();
		}

		@Override
		public String prefix() {
			return "kafka.";
		}
	},

	/**
	 * Span created on the Kafka consumer side when using a MessageListener.
	 */
	KAFKA_ON_MESSAGE_SPAN {
		@Override
		public String getName() {
			return "kafka.on-message";
		}

		@Override
		public TagKey[] getTagKeys() {
			return ConsumerTags.values();
		}

		@Override
		public String prefix() {
			return "kafka.";
		}
	},

	/**
	 * Span created on the Kafka consumer side.
	 */
	KAFKA_PRODUCER_SPAN {
		@Override
		public String getName() {
			return "kafka.produce";
		}

		@Override
		public TagKey[] getTagKeys() {
			return ProducerTags.values();
		}

		@Override
		public String prefix() {
			return "kafka.";
		}
	};

	enum ConsumerTags implements TagKey {

		/**
		 * Name of the Kafka topic.
		 */
		TOPIC {
			@Override
			public String getKey() {
				return "kafka.topic";
			}
		},

		/**
		 * Kafka offset number.
		 */
		OFFSET {
			@Override
			public String getKey() {
				return "kafka.offset";
			}
		},

		/**
		 * Kafka partition number.
		 */
		PARTITION {
			@Override
			public String getKey() {
				return "kafka.partition";
			}
		},

	}

	enum ProducerTags implements TagKey {

		/**
		 * Name of the Kafka topic.
		 */
		TOPIC {
			@Override
			public String getKey() {
				return "kafka.topic";
			}
		}

	}

}
