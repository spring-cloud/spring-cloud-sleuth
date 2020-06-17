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

package org.springframework.cloud.sleuth.autoconfig;

import java.util.ArrayList;
import java.util.List;

import brave.baggage.BaggagePropagationConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth settings.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.11
 */
@ConfigurationProperties("spring.sleuth")
// TODO: Hide in 3.x, if it isn't already deleted
public class SleuthProperties {

	private static final Log log = LogFactory.getLog(SleuthProperties.class);

	private boolean enabled = true;

	/** When true, generate 128-bit trace IDs instead of 64-bit ones. */
	private boolean traceId128 = false;

	/**
	 * True means the tracing system supports sharing a span ID between a client and
	 * server.
	 */
	private boolean supportsJoin = true;

	/**
	 * List of baggage key names that should be propagated out of process. These keys will
	 * be prefixed with `baggage` before the actual key. This property is set in order to
	 * be backward compatible with previous Sleuth versions.
	 *
	 * @see brave.propagation.ExtraFieldPropagation.FactoryBuilder#addPrefixedFields(String,
	 * java.util.Collection)
	 */
	@Deprecated
	private List<String> baggageKeys = new ArrayList<>();

	/**
	 * List of fields that are referenced the same in-process as it is on the wire. For
	 * example, the name "x-vcap-request-id" would be set as-is including the prefix.
	 *
	 * <p>
	 * Note: {@code fieldName} will be implicitly lower-cased.
	 *
	 * @see brave.propagation.ExtraFieldPropagation.FactoryBuilder#addField(String)
	 * @deprecated use {@code spring.sleuth.baggage.remote-fields} property
	 */
	@Deprecated
	private List<String> propagationKeys = new ArrayList<>();

	/**
	 * Same as {@link #propagationKeys} except that this field is not propagated to remote
	 * services.
	 *
	 * @see brave.propagation.ExtraFieldPropagation.FactoryBuilder#addRedactedField(String)
	 * @deprecated use {@code spring.sleuth.baggage.local-fields} property
	 */
	@Deprecated
	private List<String> localKeys = new ArrayList<>();

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
		log.warn("[spring.sleuth.baggage-keys] will be removed in a future release."
				+ "To change header names define a @Bean of type "
				+ BaggagePropagationConfig.SingleBaggageField.class.getName()
				+ ". The preferable approach is to migrate "
				+ "to using [spring.sleuth.baggage.remote-keys]. The [spring.sleuth.baggage-keys] would prefix the headers "
				+ "with [baggage_] and [baggage-] so unless all of your applications migrate, to remain backward compatible "
				+ "you would have to add e.g. for [spring.sleuth.baggage-keys=foo] an entry "
				+ "[spring.sleuth.baggage.remote-keys=foo,baggage-foo,baggage_foo] and eventually migrate to [spring.sleuth.baggage.remote-keys=foo]");
		this.baggageKeys = baggageKeys;
	}

	public List<String> getPropagationKeys() {
		return this.propagationKeys;
	}

	public void setPropagationKeys(List<String> propagationKeys) {
		warning("spring.sleuth.propagation-keys", "spring.sleuth.baggage.remote-fields");
		this.propagationKeys = propagationKeys;
	}

	public List<String> getLocalKeys() {
		return this.localKeys;
	}

	public void setLocalKeys(List<String> localKeys) {
		warning("spring.sleuth.local-keys", "spring.sleuth.baggage.local-fields");
		this.localKeys = localKeys;
	}

	private void warning(String currentKey, String newKey) {
		log.warn("The [" + currentKey
				+ "] property is deprecated and is removed in the next major release version of Sleuth. Please use ["
				+ newKey + "]");
	}

}
