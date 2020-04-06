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

package org.springframework.cloud.sleuth.log;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import brave.baggage.BaggageField;
import brave.baggage.BaggageFields;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import brave.baggage.CorrelationScopeDecorator;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.TraceContext;
import org.slf4j.MDC;

import org.springframework.cloud.sleuth.autoconfig.SleuthProperties;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId", "spanId" and "spanExportable"
 * when a {@link brave.Tracer#currentSpan() span is current}. These can be used in log
 * correlation. Supports backward compatibility of MDC entries by adding legacy "X-B3"
 * entries to MDC context "X-B3-TraceId", "X-B3-ParentSpanId", "X-B3-SpanId" and
 * "X-B3-Sampled"
 *
 * @author Marcin Grzejszczak
 * @since 2.1.0
 */
final class Slf4jScopeDecorator implements ScopeDecorator {

	// Backward compatibility for all logging patterns
	private static final ScopeDecorator LEGACY_IDS = MDCScopeDecorator.newBuilder()
			.clear()
			.add(SingleCorrelationField.newBuilder(BaggageFields.TRACE_ID)
					.name("X-B3-TraceId").build())
			.add(SingleCorrelationField.newBuilder(BaggageFields.PARENT_ID)
					.name("X-B3-ParentSpanId").build())
			.add(SingleCorrelationField.newBuilder(BaggageFields.SPAN_ID)
					.name("X-B3-SpanId").build())
			.add(SingleCorrelationField.newBuilder(BaggageFields.SAMPLED)
					.name("X-Span-Export").build())
			.build();

	private final ScopeDecorator delegate;

	Slf4jScopeDecorator(SleuthProperties sleuthProperties,
			SleuthSlf4jProperties sleuthSlf4jProperties) {
		CorrelationScopeDecorator.Builder builder = MDCScopeDecorator.newBuilder().clear()
				.add(SingleCorrelationField.create(BaggageFields.TRACE_ID))
				.add(SingleCorrelationField.create(BaggageFields.PARENT_ID))
				.add(SingleCorrelationField.create(BaggageFields.SPAN_ID))
				.add(SingleCorrelationField.newBuilder(BaggageFields.SAMPLED)
						.name("spanExportable").build());

		Set<String> whitelist = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		whitelist.addAll(sleuthSlf4jProperties.getWhitelistedMdcKeys());

		// Note: we are adding all the keys as-is because correlation context doesn't
		// prefix, only ExtraFieldPropagation does
		Set<String> retained = new LinkedHashSet<>();
		retained.addAll(sleuthProperties.getBaggageKeys());
		retained.addAll(sleuthProperties.getPropagationKeys());
		retained.retainAll(whitelist);

		// For backwards compatibility set all fields dirty, so that any changes made by
		// MDC directly are reverted.
		for (String name : retained) {
			builder.add(SingleCorrelationField.newBuilder(BaggageField.create(name))
					.dirty().build());
		}

		this.delegate = builder.build();
	}

	@Override
	public Scope decorateScope(TraceContext context, Scope scope) {
		return LEGACY_IDS.decorateScope(context, delegate.decorateScope(context, scope));
	}

}
