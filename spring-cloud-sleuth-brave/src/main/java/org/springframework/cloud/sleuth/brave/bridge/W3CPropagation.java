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

package org.springframework.cloud.sleuth.brave.bridge;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.internal.baggage.BaggageFields;
import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.BaggageInScope;
import org.springframework.cloud.sleuth.internal.EncodingUtils;
import org.springframework.util.StringUtils;

import static java.util.Collections.singletonList;

/**
 * Adopted from OpenTelemetry API.
 *
 * Implementation of the TraceContext propagation protocol. See <a
 * href=https://github.com/w3c/distributed-tracing>w3c/distributed-tracing</a>.
 *
 * @author OpenTelemetry Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class W3CPropagation extends Propagation.Factory implements Propagation<String> {

	private static final Log logger = LogFactory.getLog(W3CPropagation.class.getName());

	static final String TRACE_PARENT = "traceparent";

	static final String TRACE_STATE = "tracestate";

	private static final List<String> FIELDS = Collections.unmodifiableList(Arrays.asList(TRACE_PARENT, TRACE_STATE));

	private static final String VERSION = "00";

	private static final int VERSION_SIZE = 2;

	private static final char TRACEPARENT_DELIMITER = '-';

	private static final int TRACEPARENT_DELIMITER_SIZE = 1;

	private static final int LONG_BYTES = Long.SIZE / Byte.SIZE;

	private static final int BYTE_BASE16 = 2;

	private static final int LONG_BASE16 = BYTE_BASE16 * LONG_BYTES;

	private static final int TRACE_ID_HEX_SIZE = 2 * LONG_BASE16;

	private static final int SPAN_ID_SIZE = 8;

	private static final int SPAN_ID_HEX_SIZE = 2 * SPAN_ID_SIZE;

	private static final int FLAGS_SIZE = 1;

	private static final int TRACE_OPTION_HEX_SIZE = 2 * FLAGS_SIZE;

	private static final int TRACE_ID_OFFSET = VERSION_SIZE + TRACEPARENT_DELIMITER_SIZE;

	private static final int SPAN_ID_OFFSET = TRACE_ID_OFFSET + TRACE_ID_HEX_SIZE + TRACEPARENT_DELIMITER_SIZE;

	private static final int TRACE_OPTION_OFFSET = SPAN_ID_OFFSET + SPAN_ID_HEX_SIZE + TRACEPARENT_DELIMITER_SIZE;

	private static final int TRACEPARENT_HEADER_SIZE = TRACE_OPTION_OFFSET + TRACE_OPTION_HEX_SIZE;

	private static final String INVALID_TRACE_ID = "00000000000000000000000000000000";

	private static final String INVALID_SPAN_ID = "0000000000000000";

	// private static final char TRACESTATE_ENTRY_DELIMITER = ',';

	private static final Set<String> VALID_VERSIONS;

	private static final String VERSION_00 = "00";

	static {
		// A valid version is 1 byte representing an 8-bit unsigned integer, version ff is
		// invalid.
		VALID_VERSIONS = new HashSet<>();
		for (int i = 0; i < 255; i++) {
			String version = Long.toHexString(i);
			if (version.length() < 2) {
				version = '0' + version;
			}
			VALID_VERSIONS.add(version);
		}
	}

	private final W3CBaggagePropagator baggagePropagator;

	private final BraveBaggageManager braveBaggageManager;

	W3CPropagation(BraveBaggageManager braveBaggageManager, List<String> localFields) {
		this.baggagePropagator = new W3CBaggagePropagator(braveBaggageManager, localFields);
		this.braveBaggageManager = braveBaggageManager;
	}

	@Override
	public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
		return StringPropagationAdapter.create(this, keyFactory);
	}

	@Override
	public List<String> keys() {
		return FIELDS;
	}

	@Override
	public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
		return (context, carrier) -> {
			Objects.requireNonNull(context, "context");
			Objects.requireNonNull(setter, "setter");
			char[] chars = TemporaryBuffers.chars(TRACEPARENT_HEADER_SIZE);
			chars[0] = VERSION.charAt(0);
			chars[1] = VERSION.charAt(1);
			chars[2] = TRACEPARENT_DELIMITER;
			String traceId = padLeftWithZeros(context.traceIdString(), TRACE_ID_HEX_SIZE);
			for (int i = 0; i < traceId.length(); i++) {
				chars[TRACE_ID_OFFSET + i] = traceId.charAt(i);
			}
			chars[SPAN_ID_OFFSET - 1] = TRACEPARENT_DELIMITER;
			String spanId = context.spanIdString();
			for (int i = 0; i < spanId.length(); i++) {
				chars[SPAN_ID_OFFSET + i] = spanId.charAt(i);
			}
			chars[TRACE_OPTION_OFFSET - 1] = TRACEPARENT_DELIMITER;
			copyTraceFlagsHexTo(chars, TRACE_OPTION_OFFSET, context);
			setter.put(carrier, TRACE_PARENT, new String(chars, 0, TRACEPARENT_HEADER_SIZE));
			addTraceState(setter, context, carrier);
			this.baggagePropagator.injector(setter).inject(context, carrier);
		};
	}

	private <R> void addTraceState(Setter<R, String> setter, TraceContext context, R carrier) {
		if (carrier != null) {
			BaggageInScope baggage = this.braveBaggageManager.getBaggage(BraveTraceContext.fromBrave(context),
					TRACE_STATE);
			if (baggage == null) {
				return;
			}
			String traceState = baggage.get(BraveTraceContext.fromBrave(context));
			if (StringUtils.hasText(traceState)) {
				setter.put(carrier, TRACE_STATE, traceState);
			}
		}
	}

	private String padLeftWithZeros(String string, int length) {
		if (string.length() >= length) {
			return string;
		}
		else {
			StringBuilder sb = new StringBuilder(length);
			for (int i = string.length(); i < length; i++) {
				sb.append('0');
			}

			return sb.append(string).toString();
		}
	}

	void copyTraceFlagsHexTo(char[] dest, int destOffset, TraceContext context) {
		dest[destOffset] = '0';
		dest[destOffset + 1] = Boolean.TRUE.equals(context.sampled()) ? '1' : '0';
	}

	@Override
	public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
		Objects.requireNonNull(getter, "getter");
		return carrier -> {
			String traceParent = getter.get(carrier, TRACE_PARENT);
			if (traceParent == null) {
				return withBaggage(TraceContextOrSamplingFlags.EMPTY, carrier, getter);
			}
			TraceContext contextFromParentHeader = extractContextFromTraceParent(traceParent);
			if (contextFromParentHeader == null) {
				return withBaggage(TraceContextOrSamplingFlags.EMPTY, carrier, getter);
			}
			String traceStateHeader = getter.get(carrier, TRACE_STATE);
			return withBaggage(context(contextFromParentHeader, traceStateHeader), carrier, getter);
		};
	}

	private <R> TraceContextOrSamplingFlags withBaggage(TraceContextOrSamplingFlags context, R carrier,
			Getter<R, String> getter) {
		if (context.context() == null) {
			return context;
		}
		return this.baggagePropagator.contextWithBaggage(carrier, context, getter);
	}

	TraceContextOrSamplingFlags context(TraceContext contextFromParentHeader, String traceStateHeader) {
		if (!StringUtils.hasText(traceStateHeader)) {
			return TraceContextOrSamplingFlags.create(contextFromParentHeader);
		}
		try {
			return TraceContextOrSamplingFlags
					.newBuilder(TraceContext.newBuilder().traceId(contextFromParentHeader.traceId())
							.traceIdHigh(contextFromParentHeader.traceIdHigh()).spanId(contextFromParentHeader.spanId())
							.sampled(contextFromParentHeader.sampled()).shared(true).build())
					.build();
		}
		catch (IllegalArgumentException e) {
			logger.info("Unparseable tracestate header. Returning span context without state.");
			return TraceContextOrSamplingFlags.create(contextFromParentHeader);
		}
	}

	private static boolean isTraceIdValid(CharSequence traceId) {
		return (traceId.length() == TRACE_ID_HEX_SIZE) && !INVALID_TRACE_ID.contentEquals(traceId)
				&& EncodingUtils.isValidBase16String(traceId);
	}

	private static boolean isSpanIdValid(String spanId) {
		return (spanId.length() == SPAN_ID_HEX_SIZE) && !INVALID_SPAN_ID.equals(spanId)
				&& EncodingUtils.isValidBase16String(spanId);
	}

	private static TraceContext extractContextFromTraceParent(String traceparent) {
		// TODO(bdrutu): Do we need to verify that version is hex and that
		// for the version the length is the expected one?
		boolean isValid = (traceparent.length() == TRACEPARENT_HEADER_SIZE
				|| (traceparent.length() > TRACEPARENT_HEADER_SIZE
						&& traceparent.charAt(TRACEPARENT_HEADER_SIZE) == TRACEPARENT_DELIMITER))
				&& traceparent.charAt(TRACE_ID_OFFSET - 1) == TRACEPARENT_DELIMITER
				&& traceparent.charAt(SPAN_ID_OFFSET - 1) == TRACEPARENT_DELIMITER
				&& traceparent.charAt(TRACE_OPTION_OFFSET - 1) == TRACEPARENT_DELIMITER;
		if (!isValid) {
			logger.info("Unparseable traceparent header. Returning INVALID span context.");
			return null;
		}

		try {
			String version = traceparent.substring(0, 2);
			if (!VALID_VERSIONS.contains(version)) {
				return null;
			}
			if (version.equals(VERSION_00) && traceparent.length() > TRACEPARENT_HEADER_SIZE) {
				return null;
			}

			String traceId = traceparent.substring(TRACE_ID_OFFSET, TRACE_ID_OFFSET + TRACE_ID_HEX_SIZE);
			String spanId = traceparent.substring(SPAN_ID_OFFSET, SPAN_ID_OFFSET + SPAN_ID_HEX_SIZE);
			if (isTraceIdValid(traceId) && isSpanIdValid(spanId)) {
				String traceIdHigh = traceId.substring(0, traceId.length() / 2);
				String traceIdLow = traceId.substring(traceId.length() / 2);
				byte isSampled = TraceFlags.byteFromHex(traceparent, TRACE_OPTION_OFFSET);
				return TraceContext.newBuilder().shared(true)
						.traceIdHigh(EncodingUtils.longFromBase16String(traceIdHigh))
						.traceId(EncodingUtils.longFromBase16String(traceIdLow))
						.spanId(EncodingUtils.longFromBase16String(spanId)).sampled(isSampled == TraceFlags.IS_SAMPLED)
						.build();
			}
			return null;
		}
		catch (IllegalArgumentException e) {
			logger.info("Unparseable traceparent header. Returning INVALID span context.");
			return null;
		}
	}

}

/**
 * Taken from OpenTelemetry API.
 */
class W3CBaggagePropagator {

	private static final Log log = LogFactory.getLog(W3CBaggagePropagator.class);

	private static final String TRACE_STATE = "tracestate";

	private static final BaggageField TRACE_STATE_BAGGAGE = BaggageField.create(TRACE_STATE);

	private static final String FIELD = "baggage";

	private static final List<String> FIELDS = singletonList(FIELD);

	private final BraveBaggageManager braveBaggageManager;

	private final List<String> localFields;

	W3CBaggagePropagator(BraveBaggageManager braveBaggageManager, List<String> localFields) {
		this.braveBaggageManager = braveBaggageManager;
		this.localFields = localFields;
	}

	private BaggagePropagation.FactoryBuilder factory() {
		return BaggagePropagation.newFactoryBuilder(new Propagation.Factory() {
			@Override
			public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
				return null;
			}
		});
	}

	public List<String> keys() {
		return FIELDS;
	}

	public <R> TraceContext.Injector<R> injector(Propagation.Setter<R, String> setter) {
		return (context, carrier) -> {
			BaggageFields extra = context.findExtra(BaggageFields.class);
			if (extra == null || extra.getAllFields().isEmpty()) {
				return;
			}
			StringBuilder headerContent = new StringBuilder();
			// We ignore local keys - they won't get propagated
			String[] strings = this.localFields.toArray(new String[0]);
			Map<String, String> filtered = extra.toMapFilteringFieldNames(strings);
			for (Map.Entry<String, String> entry : filtered.entrySet()) {
				if (TRACE_STATE.equalsIgnoreCase(entry.getKey())) {
					continue;
				}
				headerContent.append(entry.getKey()).append("=").append(entry.getValue());
				// TODO: [OTEL] No metadata support
				// String metadataValue = entry.getEntryMetadata().getValue();
				// if (metadataValue != null && !metadataValue.isEmpty()) {
				// headerContent.append(";").append(metadataValue);
				// }
				headerContent.append(",");
			}
			if (headerContent.length() > 0) {
				headerContent.setLength(headerContent.length() - 1);
				setter.put(carrier, FIELD, headerContent.toString());
			}
		};
	}

	<R> TraceContextOrSamplingFlags contextWithBaggage(R carrier, TraceContextOrSamplingFlags flags,
			Propagation.Getter<R, String> getter) {
		BaggagePropagation.FactoryBuilder factoryBuilder = factory();
		String traceState = getter.get(carrier, TRACE_STATE);
		boolean hasTraceState = StringUtils.hasText(traceState);
		if (hasTraceState) {
			factoryBuilder = factoryBuilder
					.add(BaggagePropagationConfig.SingleBaggageField.remote(TRACE_STATE_BAGGAGE));
		}
		String baggageHeader = getter.get(carrier, FIELD);
		List<AbstractMap.SimpleEntry<BaggageInScope, String>> pairs = baggageHeader == null || baggageHeader.isEmpty()
				? Collections.emptyList() : addBaggageToContext(baggageHeader);
		Set<String> names = pairs.stream().map(e -> e.getKey().name()).collect(Collectors.toSet());
		for (String name : names) {
			factoryBuilder = factoryBuilder.add(BaggagePropagationConfig.SingleBaggageField
					.remote(((BraveBaggageInScope) this.braveBaggageManager.createBaggage(name)).unwrap()));
		}
		TraceContext decoratedContext = factoryBuilder.build().decorate(flags.context());
		if (hasTraceState) {
			BaggageInScope baggageInScope = this.braveBaggageManager.createBaggage(TRACE_STATE);
			baggageInScope.set(new BraveTraceContext(decoratedContext), traceState);
		}
		pairs.forEach(e -> {
			BaggageField baggage = ((BraveBaggageInScope) e.getKey()).unwrap();
			baggage.updateValue(decoratedContext, e.getValue());
		});
		return TraceContextOrSamplingFlags.create(decoratedContext);
	}

	List<AbstractMap.SimpleEntry<BaggageInScope, String>> addBaggageToContext(String baggageHeader) {
		List<AbstractMap.SimpleEntry<BaggageInScope, String>> pairs = new ArrayList<>();
		String[] entries = baggageHeader.split(",");
		for (String entry : entries) {
			int beginningOfMetadata = entry.indexOf(";");
			if (beginningOfMetadata > 0) {
				entry = entry.substring(0, beginningOfMetadata);
			}
			String[] keyAndValue = entry.split("=");
			for (int i = 0; i < keyAndValue.length; i += 2) {
				try {
					String key = keyAndValue[i].trim();
					String value = keyAndValue[i + 1].trim();
					BaggageInScope baggage = this.braveBaggageManager.createBaggage(key);
					pairs.add(new AbstractMap.SimpleEntry<>(baggage, value));
				}
				catch (Exception e) {
					if (log.isDebugEnabled()) {
						log.debug("Exception occurred while trying to parse baggage with key value ["
								+ Arrays.toString(keyAndValue) + "]. Will ignore that entry.", e);
					}
				}
			}
		}
		return pairs;
	}

}

/**
 * Taken from OpenTelemetry API.
 *
 * {@link ThreadLocal} buffers for use when creating new derived objects such as
 * {@link String}s. These buffers are reused within a single thread - it is _not safe_ to
 * use the buffer to generate multiple derived objects at the same time because the same
 * memory will be used. In general, you should get a temporary buffer, fill it with data,
 * and finish by converting into the derived object within the same method to avoid
 * multiple usages of the same buffer.
 */
final class TemporaryBuffers {

	private static final ThreadLocal<char[]> CHAR_ARRAY = new ThreadLocal<>();

	/**
	 * A {@link ThreadLocal} {@code char[]} of size {@code len}. Take care when using a
	 * large value of {@code len} as this buffer will remain for the lifetime of the
	 * thread. The returned buffer will not be zeroed and may be larger than the requested
	 * size, you must make sure to fill the entire content to the desired value and set
	 * the length explicitly when converting to a {@link String}.
	 */
	public static char[] chars(int len) {
		char[] buffer = CHAR_ARRAY.get();
		if (buffer == null) {
			buffer = new char[len];
			CHAR_ARRAY.set(buffer);
		}
		else if (buffer.length < len) {
			buffer = new char[len];
			CHAR_ARRAY.set(buffer);
		}
		return buffer;
	}

	// Visible for testing
	static void clearChars() {
		CHAR_ARRAY.set(null);
	}

	private TemporaryBuffers() {
	}

}

/**
 * Taken from OpenTelemetry API.
 */
final class TraceFlags {

	private TraceFlags() {
	}

	// Bit to represent whether trace is sampled or not.
	static final byte IS_SAMPLED = 0x1;

	/** Extract the byte representation of the flags from a hex-representation. */
	static byte byteFromHex(CharSequence src, int srcOffset) {
		return EncodingUtils.byteFromBase16String(src, srcOffset);
	}

}
