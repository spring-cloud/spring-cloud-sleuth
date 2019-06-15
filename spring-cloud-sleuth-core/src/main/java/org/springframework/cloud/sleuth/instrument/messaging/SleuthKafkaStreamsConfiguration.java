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

import brave.Tracing;
import brave.kafka.streams.KafkaStreamsTracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables Kafka Streams span creation and reporting.
 *
 * @author Tim te Beek
 */
@Configuration
@ConditionalOnBean(Tracing.class)
@AutoConfigureAfter({ TraceAutoConfiguration.class })
@OnMessagingEnabled
@ConditionalOnProperty(value = "spring.sleuth.messaging.kafka.streams.enabled", matchIfMissing = true)
public class SleuthKafkaStreamsConfiguration {

	protected SleuthKafkaStreamsConfiguration() {
	}

	@Bean
	@ConditionalOnMissingBean
	KafkaStreamsTracing kafkaStreamsTracing(Tracing tracing) {
		return KafkaStreamsTracing.create(tracing);
	}

	@Bean
	static KafkaStreamsBuilderFactoryBeanPostProcessor kafkaStreamsBuilderFactoryBeanPostProcessor(
			KafkaStreamsTracing kafkaStreamsTracing) {
		return new KafkaStreamsBuilderFactoryBeanPostProcessor(kafkaStreamsTracing);
	}

}

class KafkaStreamsBuilderFactoryBeanPostProcessor implements BeanPostProcessor {

	private static final Log log = LogFactory
			.getLog(KafkaStreamsBuilderFactoryBeanPostProcessor.class);

	private final KafkaStreamsTracing kafkaStreamsTracing;

	KafkaStreamsBuilderFactoryBeanPostProcessor(KafkaStreamsTracing kafkaStreamsTracing) {
		this.kafkaStreamsTracing = kafkaStreamsTracing;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof StreamsBuilderFactoryBean) {
			StreamsBuilderFactoryBean sbfb = (StreamsBuilderFactoryBean) bean;
			if (log.isDebugEnabled()) {
				log.debug(
						"StreamsBuilderFactoryBean bean is auto-configured to enable tracing.");
			}
			sbfb.setClientSupplier(kafkaStreamsTracing.kafkaClientSupplier());
		}
		return bean;
	}

}
