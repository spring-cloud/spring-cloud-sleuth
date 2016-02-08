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

package org.springframework.cloud.sleuth.assertions;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.AbstractAssert;
import org.springframework.cloud.sleuth.SpanName;

@Slf4j
public class SpanNameAssert extends AbstractAssert<SpanNameAssert, SpanName> {

	public SpanNameAssert(SpanName actual) {
		super(actual, SpanNameAssert.class);
	}

	public static SpanNameAssert then(SpanName actual) {
		return new SpanNameAssert(actual);
	}

	public SpanNameAssert hasProtocolEqualTo(String protocol) {
		isNotNull();
		if (!Objects.equals(this.actual.protocol, protocol)) {
			String message = String.format("Expected span name's protocol to be <%s> but was <%s>", protocol, this.actual.protocol);
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanNameAssert hasAddressEqualTo(String address) {
		isNotNull();
		if (!Objects.equals(this.actual.address, address)) {
			String message = String.format("Expected span name's address to be <%s> but was <%s>", address, this.actual.address);
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanNameAssert hasFragmentEqualTo(String fragment) {
		isNotNull();
		if (!Objects.equals(this.actual.fragment, fragment)) {
			String message = String.format("Expected span name's fragment to be <%s> but was <%s>", fragment, this.actual.fragment);
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}
}