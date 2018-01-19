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
