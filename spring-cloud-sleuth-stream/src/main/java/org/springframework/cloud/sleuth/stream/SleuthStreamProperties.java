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

package org.springframework.cloud.sleuth.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Dave Syer
 */
@ConfigurationProperties("spring.sleuth.stream")
public class SleuthStreamProperties {
	private boolean enabled = true;
	private String group = SleuthSink.INPUT;

	public SleuthStreamProperties() {
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public String getGroup() {
		return this.group;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof SleuthStreamProperties))
			return false;
		final SleuthStreamProperties other = (SleuthStreamProperties) o;
		if (!other.canEqual((Object) this))
			return false;
		if (this.enabled != other.enabled)
			return false;
		final Object this$group = this.group;
		final Object other$group = other.group;
		if (this$group == null ? other$group != null : !this$group.equals(other$group))
			return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + (this.enabled ? 79 : 97);
		final Object $group = this.group;
		result = result * PRIME + ($group == null ? 0 : $group.hashCode());
		return result;
	}

	protected boolean canEqual(Object other) {
		return other instanceof SleuthStreamProperties;
	}

	public String toString() {
		return "org.springframework.cloud.sleuth.stream.SleuthStreamProperties(enabled="
				+ this.enabled + ", group=" + this.group + ")";
	}
}
