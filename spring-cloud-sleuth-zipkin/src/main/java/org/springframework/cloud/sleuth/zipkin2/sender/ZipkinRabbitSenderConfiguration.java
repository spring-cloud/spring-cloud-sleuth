/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.zipkin2.sender;

import zipkin2.reporter.Sender;
import zipkin2.reporter.amqp.RabbitMQSender;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.sleuth.zipkin2.ZipkinAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(CachingConnectionFactory.class)
@ConditionalOnMissingBean(name = ZipkinAutoConfiguration.SENDER_BEAN_NAME)
@Conditional(ZipkinSenderCondition.class)
class ZipkinRabbitSenderConfiguration {

	@Value("${spring.zipkin.rabbitmq.queue:zipkin}")
	private String queue;

	@Bean(ZipkinAutoConfiguration.SENDER_BEAN_NAME)
	Sender rabbitSender(CachingConnectionFactory connectionFactory,
			RabbitProperties config) {
		return RabbitMQSender.newBuilder()
				.connectionFactory(connectionFactory.getRabbitConnectionFactory())
				.queue(this.queue).addresses(config.determineAddresses()).build();
	}

}
