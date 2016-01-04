package org.springframework.cloud.sleuth.instrument.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.instrument.integration.TraceStompMessageChannelInterceptorTests.TestApplication;
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
@SpringApplicationConfiguration(classes = TestApplication.class)
@IntegrationTest
@DirtiesContext
public class TraceStompMessageChannelInterceptorTests implements MessageHandler {
	@Autowired
	@Qualifier("executorSubscribableChannel")
	private ExecutorSubscribableChannel channel;

	@Autowired
	private TraceManager traceManager;

	private Message<?> message;
	
	@Override
	public void handleMessage(Message<?> message) throws MessagingException {
		this.message = message;
	}
	
	@Before
	public void init() {
		this.channel.subscribe(this);
	}

	@After
	public void close() {
		TraceContextHolder.removeCurrentTrace();
		this.channel.unsubscribe(this);
	}

	@Test
	public void test_whenHeaderIsStamped_thenNoSpanCreation() {
		Message<?> message = StompMessageBuilder.fromMessage(new GenericMessage<String>("Message2")).setHeader(Trace.NOT_SAMPLED_NAME, "").build();
		
		this.channel.send(message);
		assertNotNull("message was null", this.message);
		
		String spanId = this.message.getHeaders().get(Trace.SPAN_ID_NAME, String.class);
		assertNull("spanId was not null", spanId);
		
		Assert.assertEquals(message.getPayload(), this.message.getPayload());
	}

	@Test
	public void test_whenMessageHeaderIsNotStamped_thenSpanCreation() {
		Message<?> message = StompMessageBuilder.fromMessage(new GenericMessage<String>("Message2")).build();
		this.channel.send(message);
		assertNotNull("message was null", this.message);

		String spanId = this.message.getHeaders().get(Trace.SPAN_ID_NAME, String.class);
		assertNotNull("spanId was null", spanId);

		String traceId = this.message.getHeaders().get(Trace.TRACE_ID_NAME, String.class);
		assertNotNull("traceId was null", traceId);
		assertNull(TraceContextHolder.getCurrentTrace());
	}

	@Test
	public void test_whenMessageHeaderNotStamped_thenHeaderCreation() {
		final TraceManager traceManager = this.traceManager;
		final Trace trace = traceManager.startSpan("testSendMessage", new AlwaysSampler(), null);
		Message<?> message = StompMessageBuilder.fromMessage(new GenericMessage<String>("Message2")).build();
		this.channel.send(message);
		
		traceManager.close(trace);

		assertNotNull("message was null", this.message);

		String spanId = this.message.getHeaders().get(Trace.SPAN_ID_NAME, String.class);
		assertNotNull("spanId was null", spanId);

		String traceId = this.message.getHeaders().get(Trace.TRACE_ID_NAME, String.class);
		assertNotNull("traceId was null", traceId);
		
		assertEquals("Trace context is not continued", trace.getSpan().getTraceId(), traceId);
		assertEquals("Trace context is not continued", trace.getSpan().getSpanId(), spanId);
		assertNull(TraceContextHolder.getCurrentTrace());
	}

	@Configuration
	@EnableAutoConfiguration
	static class TestApplication {
		@Autowired
		TraceStompMessageChannelInterceptor stompChannelInterceptor;
		@Bean
		public ExecutorSubscribableChannel executorSubscribableChannel() {
			ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
			channel.addInterceptor(stompChannelInterceptor);
			return channel;
		}

		@Bean
		public AlwaysSampler alwaysSampler() {
			return new AlwaysSampler();
		}
	}
}
