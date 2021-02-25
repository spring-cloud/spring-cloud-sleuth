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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.StringUtils;

/**
 * Getter for Spring Integration based communication.
 *
 * This always sets native headers in defence of STOMP issues discussed <a href=
 * "https://github.com/spring-cloud/spring-cloud-sleuth/issues/716#issuecomment-337523705">here</a>.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class MessageHeaderPropagatorGetter implements Propagator.Getter<MessageHeaderAccessor> {

	private static final Log log = LogFactory.getLog(MessageHeaderPropagatorGetter.class);

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
				log.debug("An exception happened when we tried to retrieve the [" + key + "] from message", ex);
			}
		}
		return null;
	}

	private String doGet(MessageHeaderAccessor accessor, String key) {
		if (accessor instanceof NativeMessageHeaderAccessor) {
			NativeMessageHeaderAccessor nativeAccessor = (NativeMessageHeaderAccessor) accessor;
			String result = nativeAccessor.getFirstNativeHeader(key);
			if (result != null) {
				return result;
			}
		}
		else {
			Object nativeHeaders = accessor.getHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS);
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
		return "MessageHeaderPropagatorGetter{}";
	}

}
