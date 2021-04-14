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

package org.springframework.cloud.sleuth.instrument.kafka;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

@ExtendWith(MockitoExtension.class)
public class KafkaTracingCallbackTest {

	@Mock
	Tracer tracer;

	@Mock
	Span span;

	@Mock
	Callback callback;

	@Test
	void should_call_on_completion_on_user_callback_success() {
		KafkaTracingCallback tracingCallback = new KafkaTracingCallback(callback, tracer, span);
		RecordMetadata recordMetadata = new RecordMetadata(null, 0, 0, 0, 0L, 0, 0);
		tracingCallback.onCompletion(recordMetadata, null);
		Mockito.verify(callback).onCompletion(eq(recordMetadata), isNull());
	}

	@Test
	void should_call_on_completion_on_user_callback_error() {
		KafkaTracingCallback tracingCallback = new KafkaTracingCallback(callback, tracer, span);
		tracingCallback.onCompletion(null, new RuntimeException());
		Mockito.verify(callback).onCompletion(isNull(), any(RuntimeException.class));
	}

}
