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

package org.springframework.cloud.sleuth.zipkin2;

import java.util.List;
import java.util.Map;

import org.apache.kafka.common.serialization.ByteArraySerializer;
import zipkin2.reporter.Sender;
import zipkin2.reporter.kafka.KafkaSender;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ByteArraySerializer.class)
@ConditionalOnMissingBean(name = ZipkinAutoConfiguration.SENDER_BEAN_NAME)
@Conditional(ZipkinSenderCondition.class)
@ConditionalOnProperty(value = "spring.zipkin.sender.type", havingValue = "kafka")
class ZipkinKafkaSenderConfiguration {

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(KafkaProperties.class)
	static class ZipkinKafkaSenderBeanConfiguration {

		@Value("${spring.zipkin.kafka.topic:zipkin}")
		private String topic;

		static String join(List<?> parts) {
			StringBuilder to = new StringBuilder();
			for (int i = 0, length = parts.size(); i < length; i++) {
				to.append(parts.get(i));
				if (i + 1 < length) {
					to.append(',');
				}
			}
			return to.toString();
		}

		@Bean(ZipkinAutoConfiguration.SENDER_BEAN_NAME)
		Sender kafkaSender(KafkaProperties config) {
			Map<String, Object> properties = config.buildProducerProperties();
			properties.put("key.serializer", ByteArraySerializer.class.getName());
			properties.put("value.serializer", ByteArraySerializer.class.getName());
			// Kafka expects the input to be a String, but KafkaProperties returns a list
			Object bootstrapServers = properties.get("bootstrap.servers");
			if (bootstrapServers instanceof List) {
				properties.put("bootstrap.servers", join((List) bootstrapServers));
			}
			return KafkaSender.newBuilder().topic(this.topic).overrides(properties).build();
		}

	}

}
