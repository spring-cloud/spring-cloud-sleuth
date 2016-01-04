package org.springframework.cloud.sleuth.instrument.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.instrument.integration.TraceStompMessageContextPropagationChannelInterceptorTests.TestApplication;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * 
 * @author Gaurav Rai Mazra
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes=TestApplication.class)
@IntegrationTest
public class TraceStompMessageContextPropagationChannelInterceptorTests implements MessageHandler {
	@Autowired
	@Qualifier("executorSubscribableChannel")
	private ExecutorSubscribableChannel channel;

	@Autowired
	private TraceManager traceManager;
	
	@Autowired
	private AlwaysSampler sampler;
	
	private Message<?> message;

	@Override
	public void handleMessage(Message<?> message) throws MessagingException {
		this.message = message;
	}
	
	@org.junit.Before
	public void init() {
		this.channel.subscribe(this);
	}
	
	@After
	public void close() {
		TraceContextHolder.removeCurrentTrace();
		this.channel.unsubscribe(this);
	}
	
	@Test
	public void testSpanPropagation() {
		final TraceManager traceManager = this.traceManager;
		
		Trace trace = traceManager.startSpan("testSendMessage", this.sampler, null);
		Message<?> m = StompMessageBuilder.fromMessage(new GenericMessage<String>("Message2")).build();

		this.channel.send(m);
		
		String expectedSpanId = trace.getSpan().getSpanId();
		traceManager.close(trace);
		
		Message<?> message = this.message;

		assertNotNull("message was null", message);

		String spanId = message.getHeaders().get(Trace.SPAN_ID_NAME, String.class);
		assertEquals("spanId was wrong", expectedSpanId,  spanId);

		String traceId = message.getHeaders().get(Trace.TRACE_ID_NAME, String.class);
		assertNotNull("traceId was null", traceId);
	}
	
	@Configuration
	@EnableAutoConfiguration
	static class TestApplication {
		@Autowired
		TraceStompMessageChannelInterceptor stompChannelInterceptor;
		
		@Autowired
		TraceStompMessageContextPropagationChannelInterceptor stompMessageContextChannelInterceptor;
		
		@Bean
		public ExecutorSubscribableChannel executorSubscribableChannel() {
			ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
			channel.addInterceptor(stompChannelInterceptor);
			channel.addInterceptor(stompMessageContextChannelInterceptor);
			return channel;
		}

		@Bean
		public AlwaysSampler alwaysSampler() {
			return new AlwaysSampler();
		}
	}
}
