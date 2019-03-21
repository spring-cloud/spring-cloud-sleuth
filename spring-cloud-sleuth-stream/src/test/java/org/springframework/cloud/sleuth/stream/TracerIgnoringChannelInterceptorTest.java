/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.stream;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.instrument.messaging.TraceMessageHeaders;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TracerIgnoringChannelInterceptorTest {

	@Mock MessageChannel messageChannel;
	@Mock SpanMetricReporter spanMetricReporter;
	@InjectMocks TracerIgnoringChannelInterceptor tracerIgnoringChannelInterceptor;

	@Test
	public void should_attach_not_sampled_header_to_the_message() throws Exception {
		Message<String> message = MessageBuilder.withPayload("hello").build();

		Message<?> interceptedMessage = this.tracerIgnoringChannelInterceptor.preSend(message, this.messageChannel);

		then(interceptedMessage.getHeaders().get(
				TraceMessageHeaders.SAMPLED_NAME)).isEqualTo(Span.SPAN_NOT_SAMPLED);
	}

	@Test
	public void should_ignore_metrics_when_message_payload_does_not_contain_spans() throws Exception {
		Message<String> message = MessageBuilder.withPayload("hello").build();

		this.tracerIgnoringChannelInterceptor.afterSendCompletion(message, this.messageChannel, true, null);

		verifyZeroInteractions(this.spanMetricReporter);
	}

	@Test
	public void should_increment_accepted_spans_when_message_sending_was_successful() throws Exception {
		Span span1 = Span.builder().build();
		Span span2 = Span.builder().build();
		Message<Spans> message = MessageBuilder.withPayload(new Spans(null,
				Arrays.asList(span1, span2))).build();

		this.tracerIgnoringChannelInterceptor.afterSendCompletion(message, this.messageChannel, true, null);

		BDDMockito.then(this.spanMetricReporter).should().incrementAcceptedSpans(2);
	}

	@Test
	public void should_increment_dropped_spans_when_message_sending_was_successful() throws Exception {
		Span span1 = Span.builder().build();
		Span span2 = Span.builder().build();
		Message<Spans> message = MessageBuilder.withPayload(new Spans(null,
				Arrays.asList(span1, span2))).build();

		this.tracerIgnoringChannelInterceptor.afterSendCompletion(message, this.messageChannel, false, null);

		BDDMockito.then(this.spanMetricReporter).should().incrementDroppedSpans(2);
	}
}