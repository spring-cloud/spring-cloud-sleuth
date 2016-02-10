/*
 * Copyright 2013-2016 the original author or authors.
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

import java.util.Objects;

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

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SleuthStreamProperties that = (SleuthStreamProperties) o;
		return this.enabled == that.enabled && Objects.equals(this.group, that.group);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.enabled, this.group);
	}

	@Override
	public String toString() {
		return "SleuthStreamProperties{" +
				"enabled=" + this.enabled +
				", group='" + this.group + '\'' +
				'}';
	}
}
