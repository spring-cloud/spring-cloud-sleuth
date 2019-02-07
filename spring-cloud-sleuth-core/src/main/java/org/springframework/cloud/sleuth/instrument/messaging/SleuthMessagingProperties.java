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

package org.springframework.cloud.sleuth.instrument.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties for messaging.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@ConfigurationProperties("spring.sleuth")
public class SleuthMessagingProperties {

	private Integration integration = new Integration();

	private Messaging messaging = new Messaging();

	public Integration getIntegration() {
		return this.integration;
	}

	public void setIntegration(Integration integration) {
		this.integration = integration;
	}

	public Messaging getMessaging() {
		return this.messaging;
	}

	public void setMessaging(Messaging messaging) {
		this.messaging = messaging;
	}

	/**
	 * Properties for Spring Integration.
	 *
	 * @author Marcin Grzejszczak
	 */
	public static class Integration {

		/**
		 * An array of patterns against which channel names will be matched.
		 * @see org.springframework.integration.config.GlobalChannelInterceptor#patterns()
		 * Defaults to any channel name not matching the Hystrix Stream channel name.
		 */
		private String[] patterns = new String[] { "!hystrixStreamOutput*", "*" };

		/**
		 * Enable Spring Integration sleuth instrumentation.
		 */
		private boolean enabled;

		public String[] getPatterns() {
			return this.patterns;
		}

		public void setPatterns(String[] patterns) {
			this.patterns = patterns;
		}

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

	/**
	 * Generic messaging properties.
	 *
	 * @author Marcin Grzejszczak
	 */
	public static class Messaging {

		/**
		 * Should messaging be turned on.
		 */
		private boolean enabled;

		/**
		 * Rabbit related properties.
		 */
		private Rabbit rabbit = new Rabbit();

		/**
		 * Kafka related properties.
		 */
		private Kafka kafka = new Kafka();

		/**
		 * JMS related properties.
		 */
		private Jms jms = new Jms();

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public Rabbit getRabbit() {
			return this.rabbit;
		}

		public void setRabbit(Rabbit rabbit) {
			this.rabbit = rabbit;
		}

		public Kafka getKafka() {
			return this.kafka;
		}

		public void setKafka(Kafka kafka) {
			this.kafka = kafka;
		}

		public Jms getJms() {
			return this.jms;
		}

		public void setJms(Jms jms) {
			this.jms = jms;
		}

	}

	/**
	 * RabbitMQ configuration.
	 */
	public static class Rabbit {

		private boolean enabled;

		private String remoteServiceName = "rabbitmq";

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getRemoteServiceName() {
			return this.remoteServiceName;
		}

		public void setRemoteServiceName(String remoteServiceName) {
			this.remoteServiceName = remoteServiceName;
		}

	}

	/**
	 * Kafka configuration.
	 */
	public static class Kafka {

		private boolean enabled;

		private String remoteServiceName = "kafka";

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getRemoteServiceName() {
			return this.remoteServiceName;
		}

		public void setRemoteServiceName(String remoteServiceName) {
			this.remoteServiceName = remoteServiceName;
		}

	}

	/**
	 * JMS configuration.
	 */
	public static class Jms {

		private boolean enabled;

		private String remoteServiceName = "jms";

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getRemoteServiceName() {
			return this.remoteServiceName;
		}

		public void setRemoteServiceName(String remoteServiceName) {
			this.remoteServiceName = remoteServiceName;
		}

	}

}
