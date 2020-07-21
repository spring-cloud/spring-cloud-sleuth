/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import brave.propagation.Propagation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;

/**
 * This always sets native headers in defence of STOMP issues discussed <a href=
 * "https://github.com/spring-cloud/spring-cloud-sleuth/issues/716#issuecomment-337523705">here</a>.
 *
 * @author Marcin Grzejszczak
 */
enum MessageHeaderPropagation
		implements Propagation.Setter<MessageHeaderAccessor, String>,
		Propagation.Getter<MessageHeaderAccessor, String> {

	INSTANCE;

	private static final Log log = LogFactory.getLog(MessageHeaderPropagation.class);

	private static final Set<String> DEPRECATED_HEADERS = new LinkedHashSet<>();

	private static final Map<String, String> LEGACY_HEADER_MAPPING = new HashMap<>();

	static {
		LEGACY_HEADER_MAPPING.put("X-B3-TraceId", TraceMessageHeaders.TRACE_ID_NAME);
		LEGACY_HEADER_MAPPING.put("X-B3-SpanId", TraceMessageHeaders.SPAN_ID_NAME);
		LEGACY_HEADER_MAPPING.put("X-B3-ParentSpanId",
				TraceMessageHeaders.PARENT_ID_NAME);
		LEGACY_HEADER_MAPPING.put("X-B3-Sampled", TraceMessageHeaders.SAMPLED_NAME);
		LEGACY_HEADER_MAPPING.put("X-B3-Flags", TraceMessageHeaders.SPAN_FLAGS_NAME);
		DEPRECATED_HEADERS.addAll(LEGACY_HEADER_MAPPING.keySet());
		DEPRECATED_HEADERS.addAll(LEGACY_HEADER_MAPPING.values());
	}

	static Map<String, ?> propagationHeaders(Map<String, ?> headers,
			List<String> propagationHeaders) {
		Map<String, Object> headersToCopy = new HashMap<>();
		for (Map.Entry<String, ?> entry : headers.entrySet()) {
			if (propagationHeaders.contains(entry.getKey())) {
				headersToCopy.put(entry.getKey(), entry.getValue());
			}
		}
		return headersToCopy;
	}

	static void removeAnyTraceHeaders(MessageHeaderAccessor accessor,
			List<String> keysToRemove) {
		for (String keyToRemove : keysToRemove) {
			accessor.removeHeader(keyToRemove);
			if (accessor instanceof NativeMessageHeaderAccessor) {
				NativeMessageHeaderAccessor nativeAccessor = (NativeMessageHeaderAccessor) accessor;
				if (accessor.isMutable()) {
					// 1184 native headers can be an immutable map
					ensureNativeHeadersAreMutable(nativeAccessor)
							.removeNativeHeader(keyToRemove);
				}
			}
			else {
				Object nativeHeaders = accessor
						.getHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS);
				if (nativeHeaders instanceof Map) {
					((Map) nativeHeaders).remove(keyToRemove);
				}
			}
		}
	}

	/**
	 * Since for some reason, the native headers sometimes are immutable even though the
	 * accessor says that the headers are mutable, then we have to ensure their
	 * mutability. We do so by first making a mutable copy of the native headers, then by
	 * removing the native headers from the headers map and replacing them with a mutable
	 * copy. Workaround for #1184
	 * @param nativeAccessor accessor containing (or not) native headers
	 * @return modified accessor
	 */
	private static NativeMessageHeaderAccessor ensureNativeHeadersAreMutable(
			NativeMessageHeaderAccessor nativeAccessor) {
		Map<String, List<String>> nativeHeaderMap = nativeAccessor.toNativeHeaderMap();
		nativeHeaderMap = nativeHeaderMap instanceof LinkedMultiValueMap ? nativeHeaderMap
				: new LinkedMultiValueMap<>(nativeHeaderMap);
		nativeAccessor.removeHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS);
		nativeAccessor.setHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS,
				nativeHeaderMap);
		return nativeAccessor;
	}

	static final AtomicBoolean LOGGED_PUT_DEPRECATED_HEADER = new AtomicBoolean();

	@Override
	public void put(MessageHeaderAccessor accessor, String key, String value) {
		if (DEPRECATED_HEADERS.contains(key)
				&& LOGGED_PUT_DEPRECATED_HEADER.compareAndSet(false, true)) {
			log.warn("Please update your code so that it uses the 'b3' header "
					+ "instead of " + key);
		}
		try {
			doPut(accessor, key, value);
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("An exception happened when we tried to retrieve the [" + key
						+ "] from message", ex);
			}
		}
	}

	private void doPut(MessageHeaderAccessor accessor, String key, String value) {
		accessor.setHeader(key, value);
		if (accessor instanceof NativeMessageHeaderAccessor) {
			NativeMessageHeaderAccessor nativeAccessor = (NativeMessageHeaderAccessor) accessor;
			ensureNativeHeadersAreMutable(nativeAccessor).setNativeHeader(key, value);
		}
		else {
			Object nativeHeaders = accessor
					.getHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS);
			if (nativeHeaders == null) {
				nativeHeaders = new LinkedMultiValueMap<>();
				accessor.setHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS,
						nativeHeaders);
			}
			if (nativeHeaders instanceof Map<?, ?>) {
				Map<String, List<String>> copy = toNativeHeaderMap(
						(Map<String, List<String>>) nativeHeaders);
				copy.put(key, Collections.singletonList(value));
				accessor.setHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS, copy);
			}
		}
	}

	private Map<String, List<String>> toNativeHeaderMap(Map<String, List<String>> map) {
		return (map != null ? new LinkedMultiValueMap<>(map) : Collections.emptyMap());
	}

	@Override
	public String get(MessageHeaderAccessor accessor, String key) {
		try {
			String value = doGet(accessor, key);
			if (StringUtils.hasText(value)) {
				return value;
			}
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("An exception happened when we tried to retrieve the [" + key
						+ "] from message", ex);
			}
		}
		return legacyValue(accessor, key);
	}

	private String legacyValue(MessageHeaderAccessor accessor, String key) {
		String legacyKey = LEGACY_HEADER_MAPPING.get(key);
		if (legacyKey != null) {
			return doGet(accessor, legacyKey);
		}
		return null;
	}

	static final AtomicBoolean LOGGED_GET_DEPRECATED_HEADER = new AtomicBoolean();

	private String doGet(MessageHeaderAccessor accessor, String key) {
		if (DEPRECATED_HEADERS.contains(key)
				&& LOGGED_GET_DEPRECATED_HEADER.compareAndSet(false, true)) {
			log.warn("Please update your code so that it uses the 'b3' header "
					+ "instead of " + key);
		}
		if (accessor instanceof NativeMessageHeaderAccessor) {
			NativeMessageHeaderAccessor nativeAccessor = (NativeMessageHeaderAccessor) accessor;
			String result = nativeAccessor.getFirstNativeHeader(key);
			if (result != null) {
				return result;
			}
		}
		else {
			Object nativeHeaders = accessor
					.getHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS);
			if (nativeHeaders instanceof Map) {
				Object result = ((Map) nativeHeaders).get(key);
				if (result instanceof List && !((List) result).isEmpty()) {
					return String.valueOf(((List) result).get(0));
				}
			}
		}
		Object result = accessor.getHeader(key);
		if (result != null) {
			if (result instanceof byte[]) {
				return new String((byte[]) result, StandardCharsets.UTF_8);
			}
			return result.toString();
		}
		return null;
	}

	@Override
	public String toString() {
		return "MessageHeaderPropagation{}";
	}

}
