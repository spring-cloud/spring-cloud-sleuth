/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.Map;

import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.streams.processor.internals.DefaultKafkaClientSupplier;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.instrument.messaging.TraceMessagingAutoConfiguration.SleuthKafkaConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables Kafka Streams span creation and reporting.
 *
 * @author Tim te Beek
 */
@Configuration
@ConditionalOnBean({ Tracing.class, KafkaStreamsConfiguration.class })
@ConditionalOnProperty(value = "spring.sleuth.messaging.kafka.streams.enabled", matchIfMissing = true)
@AutoConfigureAfter({ SleuthKafkaConfiguration.class })
public class SleuthKafkaStreamsConfiguration {

	protected SleuthKafkaStreamsConfiguration() {
	}

	@Bean
	static KafkaStreamsBuilderFactoryBeanPostProcessor kafkaStreamsBuilderFactoryBeanPostProcessor(
			KafkaTracing tracing) {
		return new KafkaStreamsBuilderFactoryBeanPostProcessor(tracing);
	}

}

class KafkaStreamsBuilderFactoryBeanPostProcessor implements BeanPostProcessor {

	private static final Log log = LogFactory
			.getLog(KafkaStreamsBuilderFactoryBeanPostProcessor.class);

	private final KafkaTracing kafkaTracing;

	KafkaStreamsBuilderFactoryBeanPostProcessor(KafkaTracing kafkaTracing) {
		this.kafkaTracing = kafkaTracing;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof StreamsBuilderFactoryBean) {
			StreamsBuilderFactoryBean sbfb = (StreamsBuilderFactoryBean) bean;
			if (log.isDebugEnabled()) {
				log.debug("StreamsBuilderFactoryBean bean is auto-configured to enable tracing.");
			}
			sbfb.setClientSupplier(new SleuthKafkaClientSupplier(kafkaTracing));
		}
		return bean;
	}

}

class SleuthKafkaClientSupplier extends DefaultKafkaClientSupplier {

	private final KafkaTracing kafkaTracing;

	SleuthKafkaClientSupplier(KafkaTracing kafkaTracing) {
		this.kafkaTracing = kafkaTracing;
	}

	@Override
	public Producer<byte[], byte[]> getProducer(Map<String, Object> config) {
		return kafkaTracing.producer(super.getProducer(config));
	}

	@Override
	public Consumer<byte[], byte[]> getConsumer(Map<String, Object> config) {
		return kafkaTracing.consumer(super.getConsumer(config));
	}

	@Override
	public Consumer<byte[], byte[]> getRestoreConsumer(Map<String, Object> config) {
		return kafkaTracing.consumer(super.getRestoreConsumer(config));
	}

	@Override
	public Consumer<byte[], byte[]> getGlobalConsumer(Map<String, Object> config) {
		return kafkaTracing.consumer(super.getGlobalConsumer(config));
	}
}
