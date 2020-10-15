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

package org.springframework.cloud.sleuth.otel.exporter;

import io.opentelemetry.exporters.zipkin.ZipkinSpanExporter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth settings for OpenTelemetry.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@ConfigurationProperties("spring.sleuth.otel.exporter")
public class OtelExporterProperties {

	private SleuthSpanFilter sleuthSpanFilter = new SleuthSpanFilter();

	private Zipkin zipkin = new Zipkin();

	public SleuthSpanFilter getSleuthSpanFilter() {
		return this.sleuthSpanFilter;
	}

	public void setSleuthSpanFilter(SleuthSpanFilter sleuthSpanFilter) {
		this.sleuthSpanFilter = sleuthSpanFilter;
	}

	public Zipkin getZipkin() {
		return this.zipkin;
	}

	public void setZipkin(Zipkin zipkin) {
		this.zipkin = zipkin;
	}

	/**
	 * Integrations with core Sleuth handler mechanism.
	 */
	public static class SleuthSpanFilter {

		/**
		 * This application service name.
		 */
		private boolean enabled = true;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

	/**
	 * Zipkin exporter related properties.
	 */
	public static class Zipkin {

		/**
		 * This application service name.
		 */
		private String serviceName;

		/**
		 * Instrumentation version to be used to find a Tracer.
		 */
		private String zipkinEndpoint = ZipkinSpanExporter.DEFAULT_ENDPOINT;

		public String getServiceName() {
			return this.serviceName;
		}

		public void setServiceName(String serviceName) {
			this.serviceName = serviceName;
		}

		public String getZipkinEndpoint() {
			return this.zipkinEndpoint;
		}

		public void setZipkinEndpoint(String zipkinEndpoint) {
			this.zipkinEndpoint = zipkinEndpoint;
		}

	}

}
