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

package org.springframework.cloud.sleuth.documentation;

import java.util.Objects;

class KeyValueEntry implements Comparable<KeyValueEntry> {

	final String name;

	final String description;

	KeyValueEntry(String name, String description) {
		this.name = name;
		this.description = description;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		KeyValueEntry tag = (KeyValueEntry) o;
		return Objects.equals(name, tag.name) && Objects.equals(description, tag.description);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, description);
	}

	@Override
	public int compareTo(KeyValueEntry o) {
		return name.compareTo(o.name);
	}

	@Override
	public String toString() {
		return "|" + name + "|" + description();
	}

	private String description() {
		String suffix = "";
		if (this.name.contains("%s")) {
			suffix = " (since the name contains `%s` the final value will be resolved at runtime)";
		}
		return description + suffix;
	}

}
