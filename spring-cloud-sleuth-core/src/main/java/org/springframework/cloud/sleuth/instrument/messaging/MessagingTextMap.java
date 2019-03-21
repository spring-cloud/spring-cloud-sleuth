/*
 * Copyright 2013-2016 the original author or authors.
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * A {@link SpanTextMap} abstraction over {@link MessageBuilder}
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
class MessagingTextMap implements SpanTextMap {

	private final MessageBuilder<?> delegate;

	public MessagingTextMap(MessageBuilder<?> delegate) {
		this.delegate = delegate;
	}

	@Override
	public Iterator<Map.Entry<String, String>> iterator() {
		Map<String, String> map = new HashMap<>();
		for (Map.Entry<String, Object> entry : this.delegate.build().getHeaders()
				.entrySet()) {
			if (!NativeMessageHeaderAccessor.NATIVE_HEADERS.equals(entry.getKey())) {
				map.put(entry.getKey(), String.valueOf(entry.getValue()));
			}
		}
		return map.entrySet().iterator();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void put(String key, String value) {
		if (!StringUtils.hasText(value)) {
			return;
		}
		Message<?> initialMessage = this.delegate.build();
		MessageHeaderAccessor accessor = MessageHeaderAccessor
				.getMutableAccessor(initialMessage);
		accessor.setHeader(key, value);
		if (accessor instanceof SimpMessageHeaderAccessor) {
			SimpMessageHeaderAccessor nativeAccessor = (SimpMessageHeaderAccessor) accessor;
			nativeAccessor.setNativeHeader(key, value);
		}
		else if (accessor.getHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS) != null) {
			if (accessor.getHeader(
					NativeMessageHeaderAccessor.NATIVE_HEADERS) instanceof MultiValueMap) {
				MultiValueMap<String, String> map = (MultiValueMap<String, String>) accessor
						.getHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS);
				map.add(key, value);
			}
		}
		else {
			MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
			accessor.setHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS, map);
			map.add(key, value);
		}
		this.delegate.copyHeaders(accessor.toMessageHeaders());
	}
}
