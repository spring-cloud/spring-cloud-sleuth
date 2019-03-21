/*
 * Copyright 2016-2017 the original author or authors.
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

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class MessagingTextMapTests {

	@Test
	public void vanilla() {
		MessageBuilder<String> builder = MessageBuilder.withPayload("foo");
		MessagingTextMap map = new MessagingTextMap(builder);
		map.put("foo", "bar");
		Set<String> keys = new HashSet<>();
		map.forEach(entry -> keys.add(entry.getKey()));
		assertThat(keys).contains("foo");
		@SuppressWarnings("unchecked")
		MultiValueMap<String, String> natives = (MultiValueMap<String, String>) builder.build().getHeaders().get(NativeMessageHeaderAccessor.NATIVE_HEADERS);
		assertThat(natives).containsKey("foo");
		assertThat(keys).doesNotContain(NativeMessageHeaderAccessor.NATIVE_HEADERS);
	}

	@Test
	public void nativeHeadersAlreadyExist() {
		MessageBuilder<String> builder = MessageBuilder.withPayload("foo").setHeader(
				NativeMessageHeaderAccessor.NATIVE_HEADERS, new LinkedMultiValueMap<>());
		MessagingTextMap map = new MessagingTextMap(builder);
		map.put("foo", "bar");
		Set<String> keys = new HashSet<>();
		map.forEach(entry -> keys.add(entry.getKey()));
		assertThat(keys).contains("foo");
		@SuppressWarnings("unchecked")
		MultiValueMap<String, String> natives = (MultiValueMap<String, String>) builder.build().getHeaders().get(NativeMessageHeaderAccessor.NATIVE_HEADERS);
		assertThat(natives).containsKey("foo");
		assertThat(keys).doesNotContain(NativeMessageHeaderAccessor.NATIVE_HEADERS);
	}

	@Test
	public void nativeHeaders() {
		Message<String> message = MessageBuilder.withPayload("foo").build();
		MessageBuilder<String> builder = MessageBuilder.fromMessage(message)
				.setHeaders(NativeMessageHeaderAccessor.getMutableAccessor(message));
		MessagingTextMap map = new MessagingTextMap(builder);
		map.put("foo", "bar");
		Set<String> keys = new HashSet<>();
		map.forEach(entry -> keys.add(entry.getKey()));
		assertThat(keys).contains("foo");
		@SuppressWarnings("unchecked")
		MultiValueMap<String, String> natives = (MultiValueMap<String, String>) builder.build().getHeaders().get(NativeMessageHeaderAccessor.NATIVE_HEADERS);
		assertThat(natives).containsKey("foo");
		assertThat(keys).doesNotContain(NativeMessageHeaderAccessor.NATIVE_HEADERS);
	}

}
