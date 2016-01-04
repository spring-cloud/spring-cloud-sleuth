package org.springframework.cloud.sleuth.instrument.integration;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.instrument.integration.TraceStompMessageContextPropagationChannelInterceptorTests.TestApplication;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * 
 * @author Gaurav Rai Mazra
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes=TestApplication.class)
@IntegrationTest
public class TraceStompMessageContextPropagationChannelInterceptorTests extends AbstractTraceStompIntegrationTests {

	@Test
	public void should_propagate_span_information() {
		Trace trace = givenALocallyStartedSpan();
		Message<?> m = givenMessageToBeSampled();

		whenTheMessageWasSent(m);
		String expectedTraceId = trace.getSpan().getTraceId();
		traceManager.close(trace);

		thenReceivedMessageIsNotNull();
		String traceId = thenTraceIdFromHeadersIsNotEmpty();
		then(traceId).isEqualTo(expectedTraceId);
		thenSpanIdFromHeadersIsNotEmpty();
	}

	private void thenReceivedMessageIsNotNull() {
		Message<?> message = stompMessageHandler.message;
		then(message).isNotNull();
	}

	@Configuration
	@EnableAutoConfiguration
	static class TestApplication {

		@Bean ExecutorSubscribableChannel executorSubscribableChannel(
				TraceStompMessageChannelInterceptor stompChannelInterceptor,
				TraceStompMessageContextPropagationChannelInterceptor stompMessageContextChannelInterceptor) {
			ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
			channel.addInterceptor(stompChannelInterceptor);
			channel.addInterceptor(stompMessageContextChannelInterceptor);
			return channel;
		}

		@Bean AlwaysSampler alwaysSampler() {
			return new AlwaysSampler();
		}

		@Bean StompMessageHandler stompMessageHandler() {
			return new StompMessageHandler();
		}
	}
}
