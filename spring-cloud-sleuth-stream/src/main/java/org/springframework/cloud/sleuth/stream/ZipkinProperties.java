/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Zipkin settings for Zipkin Stream client
 *
 * @author Marcin Grzejszczak
 * @since 1.0.12
 */
@ConfigurationProperties("spring.zipkin")
public class ZipkinProperties {

	private Service service = new Service();

	private Locator locator = new Locator();

	public Service getService() {
		return this.service;
	}

	public void setService(Service service) {
		this.service = service;
	}

	public Locator getLocator() {
		return this.locator;
	}

	public void setLocator(Locator locator) {
		this.locator = locator;
	}

	public static class Service {
		/** The name of the service, from which the Span was sent via Stream, that should appear in Zipkin */
		private String name;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class Locator {

		private Discovery discovery;

		public Discovery getDiscovery() {
			return this.discovery;
		}

		public void setDiscovery(Discovery discovery) {
			this.discovery = discovery;
		}

		public static class Discovery {

			/** Enabling of locating the host name via service discovery */
			private boolean enabled;

			public boolean isEnabled() {
				return this.enabled;
			}

			public void setEnabled(boolean enabled) {
				this.enabled = enabled;
			}
		}
	}
}
