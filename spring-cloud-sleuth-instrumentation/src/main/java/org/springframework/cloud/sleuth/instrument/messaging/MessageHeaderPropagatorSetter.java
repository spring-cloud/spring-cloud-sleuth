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

package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.LinkedMultiValueMap;

/**
 * Setter for Spring Integration based communication.
 *
 * This always sets native headers in defence of STOMP issues discussed <a href=
 * "https://github.com/spring-cloud/spring-cloud-sleuth/issues/716#issuecomment-337523705">here</a>.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class MessageHeaderPropagatorSetter implements Propagator.Setter<MessageHeaderAccessor> {

	private static final Log log = LogFactory.getLog(MessageHeaderPropagatorSetter.class);

	static Map<String, ?> propagationHeaders(Map<String, ?> headers, List<String> propagationHeaders) {
		Map<String, Object> headersToCopy = new HashMap<>();
		for (Map.Entry<String, ?> entry : headers.entrySet()) {
			if (propagationHeaders.contains(entry.getKey())) {
				headersToCopy.put(entry.getKey(), entry.getValue());
			}
		}
		return headersToCopy;
	}

	static void removeAnyTraceHeaders(MessageHeaderAccessor accessor, List<String> keysToRemove) {
		for (String keyToRemove : keysToRemove) {
			accessor.removeHeader(keyToRemove);
			if (accessor instanceof NativeMessageHeaderAccessor) {
				NativeMessageHeaderAccessor nativeAccessor = (NativeMessageHeaderAccessor) accessor;
				if (accessor.isMutable()) {
					// 1184 native headers can be an immutable map
					ensureNativeHeadersAreMutable(nativeAccessor).removeNativeHeader(keyToRemove);
				}
			}
			else {
				Object nativeHeaders = accessor.getHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS);
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
		nativeAccessor.setHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS, nativeHeaderMap);
		return nativeAccessor;
	}

	@Override
	public void set(MessageHeaderAccessor accessor, String key, String value) {
		try {
			doPut(accessor, key, value);
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("An exception happened when we tried to retrieve the [" + key + "] from message", ex);
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
			Object nativeHeaders = accessor.getHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS);
			if (nativeHeaders == null) {
				nativeHeaders = new LinkedMultiValueMap<>();
				accessor.setHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS, nativeHeaders);
			}
			if (nativeHeaders instanceof Map<?, ?>) {
				Map<String, List<String>> copy = toNativeHeaderMap((Map<String, List<String>>) nativeHeaders);
				copy.put(key, Collections.singletonList(value));
				accessor.setHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS, copy);
			}
		}
	}

	private Map<String, List<String>> toNativeHeaderMap(Map<String, List<String>> map) {
		return (map != null ? new LinkedMultiValueMap<>(map) : Collections.emptyMap());
	}

	@Override
	public String toString() {
		return "MessageHeaderPropagatorSetter{}";
	}

}
