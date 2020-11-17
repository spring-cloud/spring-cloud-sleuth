/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.benchmarks.jmh;

import org.springframework.cloud.sleuth.brave.autoconfig.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.otel.autoconfig.OtelAutoConfiguration;

public enum TracerImplementation {

	otel(BraveAutoConfiguration.class.getCanonicalName()), brave(OtelAutoConfiguration.class.getCanonicalName());

	private String key = "spring.autoconfigure.exclude";

	private String value;

	TracerImplementation(String value) {
		this.value = value;
	}

	public String property() {
		return "--" + this.key + "=" + this.value;
	}

	@Override
	public String toString() {
		return this.name();
	}

}