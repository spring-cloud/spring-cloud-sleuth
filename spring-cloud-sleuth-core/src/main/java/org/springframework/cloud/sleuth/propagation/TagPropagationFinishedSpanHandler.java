/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.propagation;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import brave.Tags;
import brave.baggage.BaggageField;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;

/**
 * Finish span handler which adds baggage to span tags, so spans could be looked up by a
 * baggage name.
 *
 * @author Taras Danylchuk
 * @since 2.1.0
 */
public class TagPropagationFinishedSpanHandler extends FinishedSpanHandler {

	private final Set<BaggageField> baggageToTag = new LinkedHashSet<>();

	public TagPropagationFinishedSpanHandler(
			SleuthTagPropagationProperties tagPropagationProperties) {
		Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		keys.addAll(tagPropagationProperties.getWhitelistedKeys());
		keys.forEach(key -> baggageToTag.add(BaggageField.create(key)));
	}

	@Override
	public boolean handle(TraceContext context, MutableSpan span) {
		for (BaggageField field : baggageToTag) {
			Tags.BAGGAGE_FIELD.tag(field, context, span);
		}
		return true;
	}

}
