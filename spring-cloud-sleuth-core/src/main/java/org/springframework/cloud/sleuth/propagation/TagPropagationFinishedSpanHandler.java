/*
 * Copyright 2013-2021 the original author or authors.
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

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.TraceContext;

import org.springframework.cloud.sleuth.autoconfig.SleuthProperties;

import static java.util.Objects.nonNull;

/**
 * Finish span handler which adds extra propagation fields to span tags, so spans could be
 * looked up by its baggage.
 *
 * @author Taras Danylchuk
 * @since 2.1.0
 * @deprecated This type should have never been public and will be hidden or removed in
 * 3.0
 */
@Deprecated
public class TagPropagationFinishedSpanHandler extends FinishedSpanHandler {

	private final SleuthProperties sleuthProperties;

	private final SleuthTagPropagationProperties tagPropagationProperties;

	public TagPropagationFinishedSpanHandler(SleuthProperties sleuthProperties,
			SleuthTagPropagationProperties tagPropagationProperties) {
		this.sleuthProperties = sleuthProperties;
		this.tagPropagationProperties = tagPropagationProperties;
	}

	@Override
	public boolean handle(TraceContext context, MutableSpan span) {
		for (List<String> strings : Arrays.asList(this.sleuthProperties.getBaggageKeys(),
				this.sleuthProperties.getPropagationKeys())) {
			for (String key : strings) {
				if (this.tagPropagationProperties.getWhitelistedKeys().contains(key)) {
					AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(
							key, ExtraFieldPropagation.get(context, key));
					if (nonNull(entry.getValue())) {
						span.tag(entry.getKey(), entry.getValue());
					}
				}
			}
		}
		return true;
	}

}
