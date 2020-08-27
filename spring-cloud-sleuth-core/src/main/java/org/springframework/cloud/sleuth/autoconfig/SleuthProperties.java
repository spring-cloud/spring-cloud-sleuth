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

package org.springframework.cloud.sleuth.autoconfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth settings.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.11
 */
@ConfigurationProperties("spring.sleuth")
class SleuthProperties {

	private boolean enabled = true;

	/** When true, generate 128-bit trace IDs instead of 64-bit ones. */
	private boolean traceId128 = false;

	/**
	 * True means the tracing system supports sharing a span ID between a client and
	 * server.
	 */
	private boolean supportsJoin = true;

	/**
	 * Properties related to handling of spans.
	 */
	private SpanHandler spanHandler = new SpanHandler();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isTraceId128() {
		return this.traceId128;
	}

	public void setTraceId128(boolean traceId128) {
		this.traceId128 = traceId128;
	}

	public boolean isSupportsJoin() {
		return this.supportsJoin;
	}

	public void setSupportsJoin(boolean supportsJoin) {
		this.supportsJoin = supportsJoin;
	}

	public SpanHandler getSpanHandler() {
		return this.spanHandler;
	}

	public void setSpanHandler(SpanHandler spanHandler) {
		this.spanHandler = spanHandler;
	}

	/**
	 * Properties related to handling of spans.
	 */
	public static class SpanHandler {

		/**
		 * Will turn on the default Sleuth handler mechanism. Might ignore exporting of
		 * certain spans;
		 */
		private boolean enabled;

		/**
		 * List of span names to ignore. They will not be sent to external systems.
		 */
		private List<String> spanNamePatternsToSkip = Arrays
				.asList("^catalogWatchTaskScheduler$");

		/**
		 * Additional list of span names to ignore. Will be appended to
		 * {@link #spanNamePatternsToSkip}.
		 */
		private List<String> additionalSpanNamePatternsToIgnore = Collections.emptyList();

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public List<String> getSpanNamePatternsToSkip() {
			return this.spanNamePatternsToSkip;
		}

		public void setSpanNamePatternsToSkip(List<String> spanNamePatternsToSkip) {
			this.spanNamePatternsToSkip = spanNamePatternsToSkip;
		}

		public List<String> getAdditionalSpanNamePatternsToIgnore() {
			return this.additionalSpanNamePatternsToIgnore;
		}

		public void setAdditionalSpanNamePatternsToIgnore(
				List<String> additionalSpanNamePatternsToIgnore) {
			this.additionalSpanNamePatternsToIgnore = additionalSpanNamePatternsToIgnore;
		}

	}

}
