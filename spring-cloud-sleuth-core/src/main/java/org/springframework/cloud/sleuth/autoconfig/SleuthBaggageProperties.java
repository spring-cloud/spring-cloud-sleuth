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

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth Baggage settings.
 *
 * @author Adrian Cole
 * @since 3.0
 */
@ConfigurationProperties("spring.sleuth.baggage")
public class SleuthBaggageProperties {

	/**
	 * context.
	 */
	private boolean correlationEnabled = true;

	/**
	 */
	private List<String> correlationFields = new ArrayList<>();

	private List<String> localFields = new ArrayList<>();

	/**
	 * List of fields that are referenced the same in-process as it is on the wire. For
	 * example, the field "x-vcap-request-id" would be set as-is including the prefix.
	 *
	 */
	private List<String> remoteFields = new ArrayList<>();

	/**
	 */
	private List<String> tagFields = new ArrayList<>();

	public boolean isCorrelationEnabled() {
		return correlationEnabled;
	}

	public void setCorrelationEnabled(boolean correlationEnabled) {
		this.correlationEnabled = correlationEnabled;
	}

	public List<String> getCorrelationFields() {
		return correlationFields;
	}

	public void setCorrelationFields(List<String> correlationFields) {
		this.correlationFields = correlationFields;
	}

	public List<String> getLocalFields() {
		return localFields;
	}

	public void setLocalFields(List<String> localFields) {
		this.localFields = localFields;
	}

	public List<String> getRemoteFields() {
		return remoteFields;
	}

	public void setRemoteFields(List<String> remoteFields) {
		this.remoteFields = remoteFields;
	}

	public List<String> getTagFields() {
		return this.tagFields;
	}

	public void setTagFields(List<String> tagFields) {
		this.tagFields = tagFields;
	}

}
