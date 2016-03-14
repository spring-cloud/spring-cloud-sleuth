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

package org.springframework.cloud.sleuth.trace;

import java.util.ArrayList;
import java.util.List;

import org.springframework.cloud.sleuth.Span.SpanBuilder;
import org.springframework.cloud.sleuth.SpanJoiner;

/**
 * @author Marcin Grzejszczak
 */
public class SpanJoinerComposite implements SpanJoiner {
	private final List<SpanJoiner> spanJoiners;

	public SpanJoinerComposite(List<SpanJoiner> spanJoiners) {
		this.spanJoiners = spanJoiners;
	}

	public SpanJoinerComposite() {
		this.spanJoiners = new ArrayList<>();
	}

	/**
	 * Iterates over all span joiners and returns first non-null
	 * {@link SpanBuilder}
	 */
	@Override
	public <T> SpanBuilder join(T carrier) {
		for (SpanJoiner spanJoiner : this.spanJoiners) {
			SpanBuilder spanBuilder = spanJoiner.join(carrier);
			if (spanBuilder != null) {
				return spanBuilder;
			}
		}
		return null;
	}
}
