/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.sampler;

import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAccessor;

/**
 * {@link Sampler} that traces only if there is already some tracing going on.
 *
 * @author Spencer Gibb
 * @since 1.0.0
 *
 * @see SpanAccessor#isTracing()
 */
public class IsTracingSampler implements Sampler {

	private final SpanAccessor accessor;

	public IsTracingSampler(SpanAccessor accessor) {
		this.accessor = accessor;
	}

	@Override
	public boolean isSampled(Span span) {
		return this.accessor.isTracing();
	}
}
