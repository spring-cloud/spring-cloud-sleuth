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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.ProducerFencedException;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.propagation.Propagator;

public class TracingKafkaProducer<K, V> implements Producer<K, V> {

	private static final Log log = LogFactory.getLog(TracingKafkaProducer.class);

	private final Producer<K, V> delegate;

	private final Tracer tracer;

	private final Propagator propagator;

	private final Propagator.Setter<ProducerRecord<?, ?>> injector;

	public TracingKafkaProducer(Producer<K, V> producer, Tracer tracer, Propagator propagator,
			Propagator.Setter<ProducerRecord<?, ?>> setter) {
		this.delegate = producer;
		this.tracer = tracer;
		this.propagator = propagator;
		this.injector = setter;
	}

	@Override
	public void initTransactions() {
		this.delegate.initTransactions();
	}

	@Override
	public void beginTransaction() throws ProducerFencedException {
		this.delegate.beginTransaction();
	}

	@Override
	public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> map, String s)
			throws ProducerFencedException {
		this.delegate.sendOffsetsToTransaction(map, s);
	}

	@Override
	public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> map,
			ConsumerGroupMetadata consumerGroupMetadata) throws ProducerFencedException {
		this.delegate.sendOffsetsToTransaction(map, consumerGroupMetadata);
	}

	@Override
	public void commitTransaction() throws ProducerFencedException {
		this.delegate.commitTransaction();
	}

	@Override
	public void abortTransaction() throws ProducerFencedException {
		this.delegate.abortTransaction();
	}

	@Override
	public Future<RecordMetadata> send(ProducerRecord<K, V> producerRecord) {
		return send(producerRecord, null);
	}

	@Override
	public Future<RecordMetadata> send(ProducerRecord<K, V> producerRecord, Callback callback) {
		Span.Builder spanBuilder = tracer.spanBuilder().kind(Span.Kind.PRODUCER).name("kafka.produce")
				.tag("kafka.topic", producerRecord.topic());
		Span span = spanBuilder.start();
		this.propagator.inject(span.context(), producerRecord, this.injector);
		try (Tracer.SpanInScope spanInScope = tracer.withSpan(span)) {
			log.info("Current span: " + tracer.currentSpan().context().spanId());
			return this.delegate.send(producerRecord, new KafkaTracingCallback(callback, tracer, span));
		}
	}

	@Override
	public void flush() {
		this.delegate.flush();
	}

	@Override
	public List<PartitionInfo> partitionsFor(String s) {
		return this.delegate.partitionsFor(s);
	}

	@Override
	public Map<MetricName, ? extends Metric> metrics() {
		return this.delegate.metrics();
	}

	@Override
	public void close() {
		this.delegate.close();
	}

	@Override
	public void close(Duration duration) {
		this.delegate.close(duration);
	}

}
