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

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

class SpanEntry implements Comparable<SpanEntry> {

	final String name;

	final String enclosingClass;

	final String enumName;

	final String description;

	final Collection<KeyValueEntry> tagKeys;

	final Collection<KeyValueEntry> events;

	SpanEntry(String name, String enclosingClass, String enumName, String description,
			Collection<KeyValueEntry> tagKeys, Collection<KeyValueEntry> events) {
		Assert.isTrue(StringUtils.hasText(name), "Span name must not be empty");
		Assert.isTrue(StringUtils.hasText(description), "Span description must not be empty");
		this.name = name;
		this.enclosingClass = enclosingClass;
		this.enumName = enumName;
		this.description = description;
		this.tagKeys = tagKeys;
		this.events = events;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SpanEntry spanEntry = (SpanEntry) o;
		return Objects.equals(name, spanEntry.name) && Objects.equals(enclosingClass, spanEntry.enclosingClass)
				&& Objects.equals(enumName, spanEntry.enumName) && Objects.equals(description, spanEntry.description)
				&& Objects.equals(tagKeys, spanEntry.tagKeys) && Objects.equals(events, spanEntry.events);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, enclosingClass, enumName, description, tagKeys, events);
	}

	@Override
	public int compareTo(SpanEntry o) {
		return enumName.compareTo(o.enumName);
	}

	@Override
	//@formatter:off
	public String toString() {
		StringBuilder text = new StringBuilder()
				.append("=== ").append(Arrays.stream(enumName.replace("_", " ").split(" ")).map(s -> StringUtils.capitalize(s.toLowerCase(Locale.ROOT))).collect(Collectors.joining(" ")))
				.append("\n\n> ").append(description).append("\n\n")
				.append("**Span name** `").append(name).append("`");
		if (name.contains("%s")) {
			text.append(" - since it contains `%s`, the name is dynamic and will be resolved at runtime.");
		}
		else {
			text.append(".");
		}
		text.append("\n\n").append("Fully qualified name of the enclosing class `").append(this.enclosingClass).append("`");
		if (!tagKeys.isEmpty()) {
			text.append("\n\n.Tag Keys\n|===\n|Name | Description\n").append(this.tagKeys.stream().map(KeyValueEntry::toString).collect(Collectors.joining("\n")))
					.append("\n|===");
		}
		if (!events.isEmpty()) {
			text.append("\n\n.Event Values\n|===\n|Name | Description\n").append(this.events.stream().map(KeyValueEntry::toString).collect(Collectors.joining("\n")))
					.append("\n|===");
		}
		return text.toString();
	}
	//@formatter:on

}
