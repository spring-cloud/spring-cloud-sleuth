/*
 * Copyright 2015 the original author or authors.
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

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.assertThat;

import org.junit.Test;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;

/**
 * @author Dave Syer
 *
 */
public class MessagingSpanInjectorTests {

	private TraceKeys traceKeys = new TraceKeys();
	private MessagingSpanInjector messagingSpanInjector = new MessagingSpanInjector(
			this.traceKeys);

	@Test
	public void spanHeadersAdded() {
		Span span = Span.builder().name("http:foo").spanId(1L).traceId(2L).build();
		Message<?> message = new GenericMessage<>("Hello World");
		MessageBuilder<?> messageBuilder = MessageBuilder.fromMessage(message);

		this.messagingSpanInjector.inject(span, messageBuilder);

		assertThat(messageBuilder.build().getHeaders()).containsKey(Span.SPAN_ID_NAME);
	}

	@Test
	public void shouldNotOverrideSpanTags() {
		Span span = spanWithStringPayloadType();
		MessageBuilder<?> messageBuilder = messageWithIntegerPayloadType();

		this.messagingSpanInjector.inject(span, messageBuilder);

		assertThat(messageBuilder.build().getHeaders()).containsKeys(Span.SPAN_ID_NAME,
				"message/payload-type");
		assertThat(span).hasATag("message/payload-type", "java.lang.String");
	}

	private Span spanWithStringPayloadType() {
		Span span = Span.builder().name("http:foo").spanId(1L).traceId(2L).build();
		span.tag("message/payload-type", "java.lang.String");
		return span;
	}

	private MessageBuilder<?> messageWithIntegerPayloadType() {
		MessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
		accessor.setHeader("message/payload-type", "java.lang.Integer");
		return MessageBuilder.withPayload("Hello World").setHeaders(accessor);
	}

	@Test
	public void nativeSpanHeadersAdded() {
		Span span = Span.builder().name("http:foo").spanId(1L).traceId(2L).build();
		MessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
		Message<String> messageToBuild = MessageBuilder.createMessage("Hello World",
				accessor.getMessageHeaders());
		MessageBuilder<String> messageBuilder = MessageBuilder
				.fromMessage(messageToBuild);

		this.messagingSpanInjector.inject(span, messageBuilder);

		Message<String> message = messageBuilder.build();
		assertThat(message.getHeaders())
				.containsKey(NativeMessageHeaderAccessor.NATIVE_HEADERS);
		MessageHeaderAccessor natives = NativeMessageHeaderAccessor
				.getMutableAccessor(message);
		assertThat(natives.getMessageHeaders()).containsKey(Span.SPAN_ID_NAME);
	}

}
