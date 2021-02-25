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
import java.util.function.BiConsumer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;

import static org.assertj.core.api.Assertions.assertThat;

public class SqsQueueMessageHandlerTests {

	@Rule
	public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

	@Mock
	TracingMethodMessageHandlerAdapter adapter;

	SqsQueueMessageHandler subject;

	@Before
	public void setup() {
		subject = new SqsQueueMessageHandler(adapter, Collections.emptyList());
	}

	@Test
	public void sqsQueueMessageHandlerDelegatesToAdapter() {
		ArgumentCaptor<Message> messageCapture = ArgumentCaptor.forClass(Message.class);
		ArgumentCaptor<MessageHandler> handlerCapture = ArgumentCaptor
				.forClass(MessageHandler.class);
		ArgumentCaptor<BiConsumer> spanTaggerCapture = ArgumentCaptor
				.forClass(BiConsumer.class);
		Mockito.doNothing().when(adapter).wrapMethodMessageHandler(
				messageCapture.capture(), handlerCapture.capture(),
				spanTaggerCapture.capture());

		subject.handleMessage(new GenericMessage<>("a"));

		Mockito.verify(adapter, Mockito.times(1)).wrapMethodMessageHandler(Mockito.any(),
				Mockito.any(), Mockito.any());
		assertThat(messageCapture.getValue().getPayload().toString()).isEqualTo("a");
		assertThat(handlerCapture.getValue()).isNotNull();
		assertThat(spanTaggerCapture.getValue()).isNotNull();
	}

}
