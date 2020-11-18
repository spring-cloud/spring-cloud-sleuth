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

package org.springframework.cloud.sleuth.brave.instrument.messaging;

import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.kafka.streams.KafkaStreamsTracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.streams.KafkaStreams;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables Kafka Streams span creation and reporting.
 *
 * @author Tim te Beek
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMessagingEnabled
@ConditionalOnBean(Tracing.class)
@ConditionalOnProperty(value = "spring.sleuth.messaging.kafka.streams.enabled", matchIfMissing = true)
@ConditionalOnClass({ KafkaStreams.class, KafkaTracing.class })
class BraveKafkaStreamsAutoConfiguration {

	protected BraveKafkaStreamsAutoConfiguration() {
	}

	/**
	 * Expose {@link KafkaStreamsTracing} as bean to allow for filter/map/peek/transform
	 * operations.
	 * @param tracing Brave Tracing instance from TraceAutoConfiguration
	 * @return instance for use in further manual instrumentation
	 */
	@Bean
	@ConditionalOnMissingBean
	static KafkaStreamsTracing kafkaStreamsTracing(Tracing tracing) {
		return KafkaStreamsTracing.create(tracing);
	}

	/**
	 * Call
	 * {@link StreamsBuilderFactoryBean#setClientSupplier(org.apache.kafka.streams.KafkaClientSupplier)}
	 * with Brave's TracingKafkaClientSupplier.
	 * @param objectProvider provides KafkaStreamsTracing; prevents eager initialization
	 * @return
	 */
	@Bean
	static KafkaStreamsBuilderFactoryBeanPostProcessor kafkaStreamsBuilderFactoryBeanPostProcessor(
			ObjectProvider<KafkaStreamsTracing> objectProvider) {
		return new KafkaStreamsBuilderFactoryBeanPostProcessor(objectProvider);
	}

}

/**
 * Invoke
 * {@link StreamsBuilderFactoryBean#setClientSupplier(org.apache.kafka.streams.KafkaClientSupplier)}
 * with {@link KafkaStreamsTracing#kafkaClientSupplier()} to enable producer/consumer
 * header injection.<br/>
 * Explicitly not using
 * {@link org.springframework.kafka.config.StreamsBuilderFactoryBeanCustomizer} as that
 * only allows for a single instance, which could conflict with a user supplied instance.
 */
class KafkaStreamsBuilderFactoryBeanPostProcessor implements BeanPostProcessor {

	private static final Log log = LogFactory.getLog(KafkaStreamsBuilderFactoryBeanPostProcessor.class);

	private final ObjectProvider<KafkaStreamsTracing> objectProvider;

	KafkaStreamsBuilderFactoryBeanPostProcessor(ObjectProvider<KafkaStreamsTracing> objectProvider) {
		this.objectProvider = objectProvider;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof StreamsBuilderFactoryBean) {
			// KafkaStreamsTracing is created in SleuthKafkaStreamsConfiguration above, so
			// should not be null here
			KafkaStreamsTracing kafkaStreamsTracing = this.objectProvider.getIfAvailable();
			((StreamsBuilderFactoryBean) bean).setClientSupplier(kafkaStreamsTracing.kafkaClientSupplier());
			if (log.isDebugEnabled()) {
				log.debug("StreamsBuilderFactoryBean bean is auto-configured to enable tracing.");
			}
		}
		return bean;
	}

}
