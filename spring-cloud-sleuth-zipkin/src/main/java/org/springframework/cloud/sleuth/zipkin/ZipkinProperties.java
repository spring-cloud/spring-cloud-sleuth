/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.zipkin")
public class ZipkinProperties {

	/** URL of the zipkin query server instance. */
	private String baseUrl = "http://localhost:9411/";
	private boolean enabled = true;
	private int flushInterval = 1;

	public ZipkinProperties() {
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public int getFlushInterval() {
		return this.flushInterval;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setFlushInterval(int flushInterval) {
		this.flushInterval = flushInterval;
	}

	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof ZipkinProperties))
			return false;
		final ZipkinProperties other = (ZipkinProperties) o;
		if (!other.canEqual((Object) this))
			return false;
		final Object this$baseUrl = this.baseUrl;
		final Object other$baseUrl = other.baseUrl;
		if (this$baseUrl == null ?
				other$baseUrl != null :
				!this$baseUrl.equals(other$baseUrl))
			return false;
		if (this.enabled != other.enabled)
			return false;
		if (this.flushInterval != other.flushInterval)
			return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $baseUrl = this.baseUrl;
		result = result * PRIME + ($baseUrl == null ? 0 : $baseUrl.hashCode());
		result = result * PRIME + (this.enabled ? 79 : 97);
		result = result * PRIME + this.flushInterval;
		return result;
	}

	protected boolean canEqual(Object other) {
		return other instanceof ZipkinProperties;
	}

	public String toString() {
		return "org.springframework.cloud.sleuth.zipkin.ZipkinProperties(baseUrl="
				+ this.baseUrl + ", enabled=" + this.enabled + ", flushInterval="
				+ this.flushInterval + ")";
	}
}
