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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

/**
 * This decorates a Kafka {@link Consumer}. It creates and completes a
 * {@link Span.Kind#CONSUMER} span for each record received. This span will be a child
 * span of the one extracted from the record headers.
 *
 * @author Anders Clausen
 * @author Flaviu Muresan
 * @since 3.1.0
 */
public class TracingKafkaConsumer<K, V> implements Consumer<K, V> {

	private final BeanFactory beanFactory;

	private final Consumer<K, V> delegate;

	private Propagator propagator;

	private Propagator.Getter<ConsumerRecord<?, ?>> extractor;

	public TracingKafkaConsumer(Consumer<K, V> consumer, BeanFactory beanFactory) {
		this.delegate = consumer;
		this.beanFactory = beanFactory;
	}

	private Propagator propagator() {
		if (this.propagator == null) {
			this.propagator = this.beanFactory.getBean(Propagator.class);
		}
		return this.propagator;
	}

	private Propagator.Getter<ConsumerRecord<?, ?>> extractor() {
		if (this.extractor == null) {
			this.extractor = (Propagator.Getter<ConsumerRecord<?, ?>>) beanFactory
					.getBeanProvider(ResolvableType.forClassWithGenerics(Propagator.Getter.class,
							ResolvableType.forType(new ParameterizedTypeReference<ConsumerRecord<?, ?>>() {
							})))
					.getIfAvailable();
		}
		return this.extractor;
	}

	@Override
	public Set<TopicPartition> assignment() {
		return this.delegate.assignment();
	}

	@Override
	public Set<String> subscription() {
		return this.delegate.subscription();
	}

	@Override
	public void subscribe(Collection<String> collection) {
		this.delegate.subscribe(collection);
	}

	@Override
	public void subscribe(Collection<String> collection, ConsumerRebalanceListener consumerRebalanceListener) {
		this.delegate.subscribe(collection, consumerRebalanceListener);
	}

	@Override
	public void assign(Collection<TopicPartition> collection) {
		this.delegate.assign(collection);
	}

	@Override
	public void subscribe(Pattern pattern, ConsumerRebalanceListener consumerRebalanceListener) {
		this.delegate.subscribe(pattern, consumerRebalanceListener);
	}

	@Override
	public void subscribe(Pattern pattern) {
		this.delegate.subscribe(pattern);
	}

	@Override
	public void unsubscribe() {
		this.delegate.unsubscribe();
	}

	@Deprecated
	@Override
	public ConsumerRecords<K, V> poll(long l) {
		ConsumerRecords<K, V> consumerRecords = this.delegate.poll(l);
		for (ConsumerRecord<K, V> consumerRecord : consumerRecords) {
			KafkaTracingUtils.buildAndFinishSpan(consumerRecord, propagator(), extractor());
		}
		return consumerRecords;
	}

	@Override
	public ConsumerRecords<K, V> poll(Duration duration) {
		ConsumerRecords<K, V> consumerRecords = this.delegate.poll(duration);
		for (ConsumerRecord<K, V> consumerRecord : consumerRecords) {
			KafkaTracingUtils.buildAndFinishSpan(consumerRecord, propagator(), extractor());
		}
		return consumerRecords;
	}

	@Override
	public void commitSync() {
		this.delegate.commitSync();
	}

	@Override
	public void commitSync(Duration duration) {
		this.delegate.commitSync(duration);
	}

	@Override
	public void commitSync(Map<TopicPartition, OffsetAndMetadata> map) {
		this.delegate.commitSync(map);
	}

	@Override
	public void commitSync(Map<TopicPartition, OffsetAndMetadata> map, Duration duration) {
		this.delegate.commitSync(map, duration);
	}

	@Override
	public void commitAsync() {
		this.delegate.commitAsync();
	}

	@Override
	public void commitAsync(OffsetCommitCallback offsetCommitCallback) {
		this.delegate.commitAsync(offsetCommitCallback);
	}

	@Override
	public void commitAsync(Map<TopicPartition, OffsetAndMetadata> map, OffsetCommitCallback offsetCommitCallback) {
		this.delegate.commitAsync(map, offsetCommitCallback);
	}

	@Override
	public void seek(TopicPartition topicPartition, long l) {
		this.delegate.seek(topicPartition, l);
	}

	@Override
	public void seek(TopicPartition topicPartition, OffsetAndMetadata offsetAndMetadata) {
		this.delegate.seek(topicPartition, offsetAndMetadata);
	}

	@Override
	public void seekToBeginning(Collection<TopicPartition> collection) {
		this.delegate.seekToBeginning(collection);
	}

	@Override
	public void seekToEnd(Collection<TopicPartition> collection) {
		this.delegate.seekToEnd(collection);
	}

	@Override
	public long position(TopicPartition topicPartition) {
		return this.delegate.position(topicPartition);
	}

	@Override
	public long position(TopicPartition topicPartition, Duration duration) {
		return this.delegate.position(topicPartition, duration);
	}

	@Override
	@Deprecated
	public OffsetAndMetadata committed(TopicPartition topicPartition) {
		return this.delegate.committed(topicPartition);
	}

	@Override
	@Deprecated
	public OffsetAndMetadata committed(TopicPartition topicPartition, Duration duration) {
		return this.delegate.committed(topicPartition, duration);
	}

	@Override
	public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> set) {
		return this.delegate.committed(set);
	}

	@Override
	public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> set, Duration duration) {
		return this.delegate.committed(set, duration);
	}

	@Override
	public Map<MetricName, ? extends Metric> metrics() {
		return this.delegate.metrics();
	}

	@Override
	public List<PartitionInfo> partitionsFor(String s) {
		return this.delegate.partitionsFor(s);
	}

	@Override
	public List<PartitionInfo> partitionsFor(String s, Duration duration) {
		return this.delegate.partitionsFor(s, duration);
	}

	@Override
	public Map<String, List<PartitionInfo>> listTopics() {
		return this.delegate.listTopics();
	}

	@Override
	public Map<String, List<PartitionInfo>> listTopics(Duration duration) {
		return this.delegate.listTopics(duration);
	}

	@Override
	public Set<TopicPartition> paused() {
		return this.delegate.paused();
	}

	@Override
	public void pause(Collection<TopicPartition> collection) {
		this.delegate.pause(collection);
	}

	@Override
	public void resume(Collection<TopicPartition> collection) {
		this.delegate.resume(collection);
	}

	@Override
	public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> map) {
		return this.delegate.offsetsForTimes(map);
	}

	@Override
	public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> map, Duration duration) {
		return this.delegate.offsetsForTimes(map, duration);
	}

	@Override
	public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> collection) {
		return this.delegate.beginningOffsets(collection);
	}

	@Override
	public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> collection, Duration duration) {
		return this.delegate.beginningOffsets(collection, duration);
	}

	@Override
	public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> collection) {
		return this.delegate.endOffsets(collection);
	}

	@Override
	public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> collection, Duration duration) {
		return this.delegate.endOffsets(collection, duration);
	}

	@Override
	public ConsumerGroupMetadata groupMetadata() {
		return this.delegate.groupMetadata();
	}

	@Override
	public void enforceRebalance() {
		this.delegate.enforceRebalance();
	}

	@Override
	public void close() {
		this.delegate.close();
	}

	@Override
	public void close(Duration duration) {
		this.delegate.close(duration);
	}

	@Override
	public void wakeup() {
		this.delegate.wakeup();
	}

	@Override
	public OptionalLong currentLag(TopicPartition topicPartition) {
		return this.delegate.currentLag(topicPartition);
	}

}
