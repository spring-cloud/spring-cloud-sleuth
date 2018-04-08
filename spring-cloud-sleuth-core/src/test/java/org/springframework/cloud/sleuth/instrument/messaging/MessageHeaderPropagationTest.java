/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.Collections;

import brave.propagation.Propagation;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.messaging.support.MessageHeaderAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class MessageHeaderPropagationTest
		extends PropagationSetterTest<MessageHeaderAccessor, String> {
	MessageHeaderAccessor carrier = new MessageHeaderAccessor();

	@Override public Propagation.KeyFactory<String> keyFactory() {
		return Propagation.KeyFactory.STRING;
	}

	@Override protected MessageHeaderAccessor carrier() {
		return carrier;
	}

	@Override protected Propagation.Setter<MessageHeaderAccessor, String> setter() {
		return MessageHeaderPropagation.INSTANCE;
	}

	@Override protected Iterable<String> read(MessageHeaderAccessor carrier, String key) {
		Object result = carrier.getHeader(key);
		return result != null ?
				Collections.singleton(result.toString()) :
				Collections.emptyList();
	}
	
	@Test
	public void testGetByteArrayValue() {
		MessageHeaderAccessor carrier = carrier();
		carrier.setHeader("X-B3-TraceId", "48485a3953bb6124".getBytes());
		carrier.setHeader("X-B3-TraceId", "48485a3953bb6124000000".getBytes());
		String value = MessageHeaderPropagation.INSTANCE.get(carrier, "X-B3-TraceId");
		assertEquals("48485a3953bb6124000000", value);
	}
	
	@Test
	public void testGetStringValue() {
		MessageHeaderAccessor carrier = carrier();
		carrier.setHeader("X-B3-TraceId", "48485a3953bb6124");
		carrier.setHeader("X-B3-TraceId", "48485a3953bb61240000000");
		String value = MessageHeaderPropagation.INSTANCE.get(carrier, "X-B3-TraceId");
		assertEquals("48485a3953bb61240000000", value);
	}
	
	@Test
	public void testGetNullValue() {
		MessageHeaderAccessor carrier = carrier();
		carrier.setHeader("X-B3-TraceId", "48485a3953bb6124");
		carrier.setHeader("X-B3-TraceId", "48485a3953bb61240000000");
		String value = MessageHeaderPropagation.INSTANCE.get(carrier, "non existent key");
		assertNull(value);
	}
}
