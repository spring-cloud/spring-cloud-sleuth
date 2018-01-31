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

import brave.propagation.Propagation;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;

/**
 * Tests that native headers are redundantly added
 */
public class MessageHeaderPropagation_NativeTest
		extends PropagationSetterTest<MessageHeaderAccessor, String> {
	NativeMessageHeaderAccessor carrier = new NativeMessageHeaderAccessor() {
	};

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
		return ((NativeMessageHeaderAccessor) carrier).getNativeHeader(key);
	}
}
