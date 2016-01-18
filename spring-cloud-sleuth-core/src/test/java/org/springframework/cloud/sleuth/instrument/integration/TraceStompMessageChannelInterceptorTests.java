package org.springframework.cloud.sleuth.instrument.integration;

import static org.assertj.core.api.BDDAssertions.then;

import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.instrument.integration.TraceStompMessageChannelInterceptorTests.TestApplication;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.GenericMessage;

/**
 *
 * @author Gaurav Rai Mazra
 *
 */

@SpringApplicationConfiguration(classes = TestApplication.class)
@IntegrationTest
public class TraceStompMessageChannelInterceptorTests extends AbstractTraceStompIntegrationTests {

	@Test
	public void should_not_create_span_if_message_contains_not_sampled_header() {
		Message<?> message = givenMessageNotToBeSampled();

		whenTheMessageWasSent(message);

		thenSpanIdFromHeadersIsEmpty();
		thenReceivedMessageIsEqualToTheSentOne(message);
	}

	@Test
	public void should_create_span_when_headers_dont_contain_not_sampled() {
		Message<?> message = givenMessageToBeSampled();

		whenTheMessageWasSent(message);

		thenSpanIdFromHeadersIsNotEmpty();
		thenTraceIdFromHeadersIsNotEmpty();
		then(TraceContextHolder.getCurrentTrace()).isNull();
	}

	@Test
	public void should_propagate_headers_when_message_was_sent_during_local_span_starting() {
		Trace trace = givenALocallyStartedSpan();
		Message<?> message = givenMessageToBeSampled();

		whenTheMessageWasSent(message);
		this.tracer.close(trace);

		Long spanId = thenSpanIdFromHeadersIsNotEmpty();
		long traceId = thenTraceIdFromHeadersIsNotEmpty();
		then(traceId).isEqualTo(trace.getSpan().getTraceId());
		then(spanId).isEqualTo(trace.getSpan().getSpanId());
		then(TraceContextHolder.getCurrentTrace()).isNull();
	}

	private Message<?> givenMessageNotToBeSampled() {
		return StompMessageBuilder.fromMessage(new GenericMessage<>("Message2")).setHeader(Trace.NOT_SAMPLED_NAME, "").build();
	}

	private String thenSpanIdFromHeadersIsEmpty() {
		String header = getValueFromHeaders(Trace.SPAN_ID_NAME, String.class);
		then(header).as("Span id should be empty").isNullOrEmpty();
		return header;
	}

	private void thenReceivedMessageIsEqualToTheSentOne(Message<?> message) {
		then(message.getPayload()).isEqualTo(this.stompMessageHandler.message.getPayload());
	}

	@Configuration
	@EnableAutoConfiguration
	static class TestApplication {

		@Bean ExecutorSubscribableChannel executorSubscribableChannel(TraceStompMessageChannelInterceptor stompChannelInterceptor) {
			ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
			channel.addInterceptor(stompChannelInterceptor);
			return channel;
		}

		@Bean StompMessageHandler stompMessageHandler() {
			return new StompMessageHandler();
		}

		@Bean AlwaysSampler alwaysSampler() {
			return new AlwaysSampler();
		}
	}
}
