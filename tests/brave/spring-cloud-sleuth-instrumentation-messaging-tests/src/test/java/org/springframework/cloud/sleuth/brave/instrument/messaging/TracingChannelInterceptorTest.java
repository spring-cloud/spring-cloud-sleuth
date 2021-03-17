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

package org.springframework.cloud.sleuth.brave.instrument.messaging;

import java.util.List;
import java.util.Map;

import brave.Tracing;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.propagation.B3Propagation;
import brave.propagation.TraceContext;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.brave.BraveTestTracing;
import org.springframework.cloud.sleuth.test.TestTracingAware;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;

import static brave.propagation.B3Propagation.Format.SINGLE;
import static brave.propagation.B3SingleFormat.parseB3SingleFormat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.messaging.support.NativeMessageHeaderAccessor.NATIVE_HEADERS;

public class TracingChannelInterceptorTest
		extends org.springframework.cloud.sleuth.instrument.messaging.TracingChannelInterceptorTest {

	BraveTestTracing testTracing;

	@Override
	public TestTracingAware tracerTest() {
		if (this.testTracing == null) {
			this.testTracing = new BraveTestTracing() {
				@Override
				public Tracing.Builder tracingBuilder() {
					return super.tracingBuilder().propagationFactory(BaggagePropagation
							.newFactoryBuilder(B3Propagation.newFactoryBuilder().injectFormat(SINGLE).build())
							.add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("Foo-Id")))
							.add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("Baz-Id")))
							.build());
				}
			};
			this.testTracing.reset();
		}
		return this.testTracing;
	}

	@Test
	public void producerConsidersOldSpanIds_nativeHeaders() {
		channel.addInterceptor(producerSideOnly(this.interceptor));

		NativeMessageHeaderAccessor accessor = new NativeMessageHeaderAccessor() {
		};

		accessor.setNativeHeader("b3", "000000000000000a-000000000000000b-1-000000000000000a");

		this.channel.send(MessageBuilder.withPayload("foo").copyHeaders(accessor.toMessageHeaders()).build());

		TraceContext receiveContext = parseB3SingleFormat(
				((List) ((Map) this.channel.receive().getHeaders().get(NATIVE_HEADERS)).get("b3")).get(0).toString())
						.context();
		assertThat(receiveContext.parentIdString()).isEqualTo("000000000000000b");
	}

	/**
	 * If the producer is acting on an un-processed message (ex via a polling consumer),
	 * it should look at trace headers when there is no span in scope, and use that as the
	 * parent context.
	 */
	@Test
	public void producerConsidersOldSpanIds() {
		this.channel.addInterceptor(producerSideOnly(this.interceptor));

		this.channel
				.send(MessageBuilder.withPayload("foo").setHeader("b3", "000000000000000a-000000000000000b-1").build());

		TraceContext receiveContext = parseB3SingleFormat(this.channel.receive().getHeaders().get("b3", String.class))
				.context();
		assertThat(receiveContext.parentIdString()).isEqualTo("000000000000000b");
	}

}
