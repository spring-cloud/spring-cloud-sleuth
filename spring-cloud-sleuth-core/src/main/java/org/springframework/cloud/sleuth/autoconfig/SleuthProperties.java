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

package org.springframework.cloud.sleuth.autoconfig;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth settings
 *
 * @since 1.0.11
 */
@ConfigurationProperties("spring.sleuth")
public class SleuthProperties {

	private boolean enabled = true;

	/** When true, generate 128-bit trace IDs instead of 64-bit ones. */
	private boolean traceId128 = false;

	/** True means the tracing system supports sharing a span ID between a client and server. */
	private boolean supportsJoin = true;

	/**
	 * List of baggage key names that should be propagated out of process.
	 * These keys will be prefixed with `baggage` before the actual key.
	 * This property is set in order to be backward compatible with previous
	 * Sleuth versions.
	 *
	 * @see brave.propagation.ExtraFieldPropagation.FactoryBuilder#addPrefixedFields(String, java.util.Collection)
	 */
	private List<String> baggageKeys = new ArrayList<>();

	/**
	 * List of fields that are referenced the same in-process as it is on the wire. For example, the
	 * name "x-vcap-request-id" would be set as-is including the prefix.
	 *
	 * <p>Note: {@code fieldName} will be implicitly lower-cased.
	 *
	 * @see brave.propagation.ExtraFieldPropagation.FactoryBuilder#addField(String)
	 */
	private List<String> propagationKeys = new ArrayList<>();

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

	public List<String> getBaggageKeys() {
		return this.baggageKeys;
	}

	public void setBaggageKeys(List<String> baggageKeys) {
		this.baggageKeys = baggageKeys;
	}

	public List<String> getPropagationKeys() {
		return this.propagationKeys;
	}

	public void setPropagationKeys(List<String> propagationKeys) {
		this.propagationKeys = propagationKeys;
	}
}
