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

import java.util.Iterator;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import org.springframework.cloud.sleuth.propagation.Propagator;

/**
 * Getter extracting the values from the {@link ConsumerRecord} headers for Kafka based
 * communication.
 *
 * @author Anders Clausen
 * @author Flaviu Muresan
 * @since 3.0.3
 */
public class TracingKafkaPropagatorGetter implements Propagator.Getter<ConsumerRecord<?, ?>> {

	@Override
	public String get(ConsumerRecord<?, ?> carrier, String key) {
		return Optional.ofNullable(carrier).map(ConsumerRecord::headers).map(headers -> headers.headers(key))
				.map(Iterable::iterator).filter(Iterator::hasNext).map(Iterator::next).map(Header::value)
				.map(String::new).orElse(null);
	}

}
