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

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.instrument.messaging.TraceMessageHeaders;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptorAdapter;

/**
 * {@link org.springframework.messaging.support.ChannelInterceptor} that doesn't
 * trace the tracer.
 *
 * @author Marcin Grzejszczak
 */
class TracerIgnoringChannelInterceptor extends ChannelInterceptorAdapter {

	private final SpanMetricReporter spanMetricReporter;

	public TracerIgnoringChannelInterceptor(SpanMetricReporter spanMetricReporter) {
		this.spanMetricReporter = spanMetricReporter;
	}

	/**
	 * Don't trace the tracer (suppress spans originating from our own source)
	 **/
	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		return MessageBuilder.fromMessage(message)
				.setHeader(TraceMessageHeaders.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED).build();
	}

	@Override
	public void afterSendCompletion(Message<?> message, MessageChannel channel,
			boolean sent, Exception ex) {
		if (!(message.getPayload() instanceof Spans)) {
			return;
		}
		Spans spans = (Spans) message.getPayload();
		int spanNumber = spans.getSpans().size();
		if (sent) {
			this.spanMetricReporter.incrementAcceptedSpans(spanNumber);
		} else {
			this.spanMetricReporter.incrementDroppedSpans(spanNumber);
		}
	}
}
