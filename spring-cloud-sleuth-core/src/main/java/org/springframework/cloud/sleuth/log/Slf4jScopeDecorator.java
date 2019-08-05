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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.cloud.sleuth.autoconfig.SleuthProperties;
import org.springframework.util.StringUtils;

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
final class Slf4jScopeDecorator implements CurrentTraceContext.ScopeDecorator {

	// Backward compatibility for all logging patterns
	private static final String LEGACY_EXPORTABLE_NAME = "X-Span-Export";

	private static final String LEGACY_PARENT_ID_NAME = "X-B3-ParentSpanId";

	private static final String LEGACY_TRACE_ID_NAME = "X-B3-TraceId";

	private static final String LEGACY_SPAN_ID_NAME = "X-B3-SpanId";

	private static final Logger log = LoggerFactory.getLogger(Slf4jScopeDecorator.class);

	private final SleuthProperties sleuthProperties;

	private final SleuthSlf4jProperties sleuthSlf4jProperties;

	Slf4jScopeDecorator(SleuthProperties sleuthProperties,
			SleuthSlf4jProperties sleuthSlf4jProperties) {
		this.sleuthProperties = sleuthProperties;
		this.sleuthSlf4jProperties = sleuthSlf4jProperties;
	}

	static void replace(String key, @Nullable String value) {
		if (value != null) {
			MDC.put(key, value);
		}
		else {
			MDC.remove(key);
		}
	}

	@Override
	public CurrentTraceContext.Scope decorateScope(TraceContext currentSpan,
			CurrentTraceContext.Scope scope) {
		final String previousTraceId = MDC.get("traceId");
		final String previousParentId = MDC.get("parentId");
		final String previousSpanId = MDC.get("spanId");
		final String spanExportable = MDC.get("spanExportable");
		final String legacyPreviousTraceId = MDC.get(LEGACY_TRACE_ID_NAME);
		final String legacyPreviousParentId = MDC.get(LEGACY_PARENT_ID_NAME);
		final String legacyPreviousSpanId = MDC.get(LEGACY_SPAN_ID_NAME);
		final String legacySpanExportable = MDC.get(LEGACY_EXPORTABLE_NAME);
		final List<AbstractMap.SimpleEntry<String, String>> previousMdc = previousMdc();

		if (currentSpan != null) {
			String traceIdString = currentSpan.traceIdString();
			MDC.put("traceId", traceIdString);
			MDC.put(LEGACY_TRACE_ID_NAME, traceIdString);
			String parentId = currentSpan.parentId() != null
					? HexCodec.toLowerHex(currentSpan.parentId()) : null;
			replace("parentId", parentId);
			replace(LEGACY_PARENT_ID_NAME, parentId);
			String spanId = HexCodec.toLowerHex(currentSpan.spanId());
			MDC.put("spanId", spanId);
			MDC.put(LEGACY_SPAN_ID_NAME, spanId);
			String sampled = String.valueOf(currentSpan.sampled());
			MDC.put("spanExportable", sampled);
			MDC.put(LEGACY_EXPORTABLE_NAME, sampled);
			log("Starting scope for span: {}", currentSpan);
			if (currentSpan.parentId() != null) {
				if (log.isTraceEnabled()) {
					log.trace("With parent: {}", currentSpan.parentId());
				}
			}
			for (String key : whitelistedBaggageKeysWithValue(currentSpan)) {
				MDC.put(key, ExtraFieldPropagation.get(currentSpan, key));
			}
			for (String key : whitelistedPropagationKeysWithValue(currentSpan)) {
				MDC.put(key, ExtraFieldPropagation.get(currentSpan, key));
			}
		}
		else {
			MDC.remove("traceId");
			MDC.remove("parentId");
			MDC.remove("spanId");
			MDC.remove("spanExportable");
			MDC.remove(LEGACY_TRACE_ID_NAME);
			MDC.remove(LEGACY_PARENT_ID_NAME);
			MDC.remove(LEGACY_SPAN_ID_NAME);
			MDC.remove(LEGACY_EXPORTABLE_NAME);
			for (String s : whitelistedBaggageKeys()) {
				MDC.remove(s);
			}
			for (String s : whitelistedPropagationKeys()) {
				MDC.remove(s);
			}
			previousMdc.clear();
		}

		/**
		 * Thread context scope.
		 *
		 * @author Adrian Cole
		 */
		class ThreadContextCurrentTraceContextScope implements CurrentTraceContext.Scope {

			@Override
			public void close() {
				log("Closing scope for span: {}", currentSpan);
				scope.close();
				replace("traceId", previousTraceId);
				replace("parentId", previousParentId);
				replace("spanId", previousSpanId);
				replace("spanExportable", spanExportable);
				replace(LEGACY_TRACE_ID_NAME, legacyPreviousTraceId);
				replace(LEGACY_PARENT_ID_NAME, legacyPreviousParentId);
				replace(LEGACY_SPAN_ID_NAME, legacyPreviousSpanId);
				replace(LEGACY_EXPORTABLE_NAME, legacySpanExportable);
				for (AbstractMap.SimpleEntry<String, String> entry : previousMdc) {
					replace(entry.getKey(), entry.getValue());
				}
			}

		}
		return new ThreadContextCurrentTraceContextScope();
	}

	private List<AbstractMap.SimpleEntry<String, String>> previousMdc() {
		List<AbstractMap.SimpleEntry<String, String>> previousMdc = new ArrayList<>();
		List<String> keys = new ArrayList<>(whitelistedBaggageKeys());
		keys.addAll(whitelistedPropagationKeys());
		for (String key : keys) {
			previousMdc.add(new AbstractMap.SimpleEntry<>(key, MDC.get(key)));
		}
		return previousMdc;
	}

	private List<String> whitelistedKeys(List<String> keysToFilter) {
		List<String> keys = new ArrayList<>();
		for (String baggageKey : keysToFilter) {
			if (this.sleuthSlf4jProperties.getWhitelistedMdcKeys().contains(baggageKey)) {
				keys.add(baggageKey);
			}
		}
		return keys;
	}

	private List<String> whitelistedBaggageKeys() {
		return whitelistedKeys(this.sleuthProperties.getBaggageKeys());
	}

	private List<String> whitelistedKeysWithValue(TraceContext context,
			List<String> keys) {
		if (context == null) {
			return Collections.EMPTY_LIST;
		}
		List<String> nonEmpty = new ArrayList<>();
		for (String key : keys) {
			if (StringUtils.hasText(ExtraFieldPropagation.get(context, key))) {
				nonEmpty.add(key);
			}
		}
		return nonEmpty;
	}

	private List<String> whitelistedBaggageKeysWithValue(TraceContext context) {
		return whitelistedKeysWithValue(context, whitelistedBaggageKeys());
	}

	private List<String> whitelistedPropagationKeys() {
		return whitelistedKeys(this.sleuthProperties.getPropagationKeys());
	}

	private List<String> whitelistedPropagationKeysWithValue(TraceContext context) {
		return whitelistedKeysWithValue(context, whitelistedPropagationKeys());
	}

	private void log(String text, TraceContext span) {
		if (span == null) {
			return;
		}
		if (log.isTraceEnabled()) {
			log.trace(text, span);
		}
	}

}
