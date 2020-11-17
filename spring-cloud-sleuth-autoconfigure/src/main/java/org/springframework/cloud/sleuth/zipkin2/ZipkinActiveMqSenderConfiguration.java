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

import org.apache.activemq.ActiveMQConnectionFactory;
import zipkin2.reporter.Sender;
import zipkin2.reporter.activemq.ActiveMQSender;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ActiveMQConnectionFactory.class)
@ConditionalOnMissingBean(name = ZipkinAutoConfiguration.SENDER_BEAN_NAME)
@Conditional(ZipkinSenderCondition.class)
@ConditionalOnProperty(value = "spring.zipkin.sender.type", havingValue = "activemq")
@AutoConfigureAfter(ActiveMQAutoConfiguration.class)
class ZipkinActiveMqSenderConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBean(ActiveMQConnectionFactory.class)
	static class ZipkinActiveMqSenderBeanConfiguration {

		@Value("${spring.zipkin.activemq.queue:zipkin}")
		private String queue;

		@Value("${spring.zipkin.activemq.message-max-bytes:100000}")
		private int messageMaxBytes;

		@Bean(ZipkinAutoConfiguration.SENDER_BEAN_NAME)
		Sender activeMqSender(ActiveMQConnectionFactory factory) {
			return ActiveMQSender.newBuilder().connectionFactory(factory).messageMaxBytes(this.messageMaxBytes)
					.queue(this.queue).build();
		}

	}

}
