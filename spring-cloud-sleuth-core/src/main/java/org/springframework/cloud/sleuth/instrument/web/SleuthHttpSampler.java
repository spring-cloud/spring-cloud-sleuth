/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.regex.Pattern;

import brave.http.HttpAdapter;
import brave.http.HttpSampler;

/**
 * Doesn't sample a span if skip pattern is matched
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
class SleuthHttpSampler extends HttpSampler {

	private final Pattern pattern;

	SleuthHttpSampler(SkipPatternProvider provider) {
		this.pattern = provider.skipPattern();
	}

	@Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
		String url = adapter.path(request);
		boolean shouldSkip = this.pattern.matcher(url).matches();
		if (shouldSkip) {
			return false;
		}
		return null;
	}
}
