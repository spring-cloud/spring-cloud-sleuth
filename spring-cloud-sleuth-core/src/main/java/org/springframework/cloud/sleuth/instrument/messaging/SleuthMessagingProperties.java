/*
 * Copyright 2013-2018 the original author or authors.
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

	public static class Integration {
		private boolean enabled;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}

	public static class Messaging {
		private boolean enabled;

		private String remoteServiceName = "mq-service";

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
