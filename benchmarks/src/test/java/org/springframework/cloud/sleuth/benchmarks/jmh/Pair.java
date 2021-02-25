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

public class Pair {
	final String key;
	final String value;

	public Pair(String key, String value) {
		this.key = key;
		this.value = value;
	}

	public String asProp() {
		return this.key + "=" + this.value;
	}

	public static Pair of(String key, String value) {
		return new Pair(key, value);
	}

	public static Pair noHook() {
		return new Pair("spring.sleuth.reactor.decorate-hooks", "false");
	}

	public static Pair noSleuth() {
		return new Pair("spring.sleuth.enabled", "false");
	}

	public static Pair onEach() {
		return new Pair("spring.sleuth.reactor.decorate-on-each", "true");
	}

	public static Pair onLast() {
		return new Pair("spring.sleuth.reactor.decorate-on-each", "false");
	}
}
