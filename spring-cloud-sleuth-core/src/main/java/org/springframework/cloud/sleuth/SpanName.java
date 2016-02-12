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

package org.springframework.cloud.sleuth;

import java.lang.invoke.MethodHandles;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Class representing a name of the span.
 * It consists of
 * <li>
 *     <ul>component - means of communication e.g. http, message</ul>
 *     <ul>address - what the span is addressing e.g. /some/http/address, someQueueName</ul>
 *     <ul>fragment - additional label e.g. async</ul>
 * </li>
 *
 * The template of the span name is
 *
 * <pre>component:address#fragment</pre>
 *
 * @author Marcin Grzejszczak
 */
public class SpanName {

	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final SpanName NO_NAME = new SpanName("", "");

	public final String component;
	public final String address;
	public final String fragment;

	public SpanName(String component, String address) {
		this(component, address, "");
	}

	public SpanName(String component, String address, String fragment) {
		this.component = component;
		this.address = address;
		this.fragment = fragment;
	}

	public static SpanName fromString(String name) {
		String[] splitString = name.split(":");
		if (splitString.length < 2) {
			log.debug("Can't parse [{}]. Returning 'unknown' component and passing name to address", name);
			return new SpanName("unknown", name);
		}
		String protocol = splitString[0];
		String address = name.substring(name.indexOf(":") + 1);
		String fragment = "";
		if (address.contains("#")) {
			String[] splitSecondArg = address.split("#");
			fragment = address.substring(address.indexOf("#") + 1);
			address = splitSecondArg[0];
		}
		return new SpanName(protocol, address, fragment);
	}

	public String toLongString() {
		if (this.equals(NO_NAME)) {
			return "";
		}
		String baseName = this.component + ":" + this.address;
		if (StringUtils.hasText(this.fragment)) {
			return baseName + "#" + this.fragment;
		}
		return baseName;
	}

	public boolean hasFragment() {
		return StringUtils.hasText(this.fragment);
	}

	@Override
	public String toString() {
		if (this.equals(NO_NAME)) {
			return "";
		}
		return this.component + ":" + this.address;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		SpanName spanName = (SpanName) o;
		return Objects.equals(this.component, spanName.component) &&
				Objects.equals(this.address, spanName.address) &&
				Objects.equals(this.fragment, spanName.fragment);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.component, this.address, this.fragment);
	}
}
