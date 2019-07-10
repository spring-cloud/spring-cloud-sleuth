/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.resource.spi.ResourceAdapter;

import brave.Tracing;
import brave.internal.HexCodec;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.activemq.ra.ActiveMQActivationSpec;
import org.apache.activemq.ra.ActiveMQResourceAdapter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Ignore;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Span;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.jms.XAConnectionFactoryWrapper;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jca.support.ResourceAdapterFactoryBean;
import org.springframework.jca.work.SimpleTaskWorkManager;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.endpoint.JmsMessageEndpointManager;

import static org.assertj.core.api.Assertions.assertThat;

// inspired by org.springframework.boot.autoconfigure.jms.JmsAutoConfigurationTests

/**
 * @author Adrian Cole
 */
public class JmsTracingConfigurationTest {

	final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(JmsTestTracingConfiguration.class,
					AnnotationJmsListenerConfiguration.class, XAConfiguration.class,
					SimpleJmsListenerConfiguration.class,
					JcaJmsListenerConfiguration.class));

	static void clearSpans(AssertableApplicationContext ctx) throws JMSException {
		ctx.getBean(JmsTestTracingConfiguration.class).clearSpan();
	}

	static void checkConnection(AssertableApplicationContext ctx) throws JMSException {
		// Not using try-with-resources as that doesn't exist in JMS 1.1
		Connection con = ctx.getBean(ConnectionFactory.class).createConnection();
		try {
			con.setExceptionListener(exception -> {
			});
			assertThat(con.getExceptionListener().getClass().getName())
					.startsWith("brave.jms.TracingExceptionListener");
		}
		finally {
			con.close();
		}
	}

	static void checkXAConnection(AssertableApplicationContext ctx) throws JMSException {
		// Not using try-with-resources as that doesn't exist in JMS 1.1
		XAConnection con = ctx.getBean(XAConnectionFactory.class).createXAConnection();
		try {
			con.setExceptionListener(exception -> {
			});
			assertThat(con.getExceptionListener().getClass().getName())
					.startsWith("brave.jms.TracingExceptionListener");
		}
		finally {
			con.close();
		}
	}

	static void checkTopicConnection(AssertableApplicationContext ctx)
			throws JMSException {
		// Not using try-with-resources as that doesn't exist in JMS 1.1
		TopicConnection con = ctx.getBean(TopicConnectionFactory.class)
				.createTopicConnection();
		try {
			con.setExceptionListener(exception -> {
			});
			assertThat(con.getExceptionListener().getClass().getName())
					.startsWith("brave.jms.TracingExceptionListener");
		}
		finally {
			con.close();
		}
	}

	@Test
	public void tracesConnectionFactory() {
		this.contextRunner.run(JmsTracingConfigurationTest::checkConnection);
	}

	@Test
	public void tracesXAConnectionFactories() {
		this.contextRunner.withUserConfiguration(XAConfiguration.class).run(ctx -> {
			clearSpans(ctx);
			checkConnection(ctx);
			checkXAConnection(ctx);
		});
	}

	@Test
	public void tracesTopicConnectionFactories() {
		this.contextRunner.withUserConfiguration(XAConfiguration.class).run(ctx -> {
			clearSpans(ctx);
			checkConnection(ctx);
			checkTopicConnection(ctx);
		});
	}

	@Test
	public void tracesListener_jmsMessageListener() {
		this.contextRunner.withUserConfiguration(SimpleJmsListenerConfiguration.class)
				.run(ctx -> {
					clearSpans(ctx);
					ctx.getBean(JmsTemplate.class).convertAndSend("myQueue", "foo");

					Callable<Span> takeSpan = ctx.getBean("takeSpan", Callable.class);
					List<Span> trace = Arrays.asList(takeSpan.call(), takeSpan.call(),
							takeSpan.call());

					assertThat(trace).allSatisfy(s -> assertThat(s.traceId())
							.isEqualTo(trace.get(0).traceId()));
					assertThat(trace).isNotNull().extracting(Span::name).contains("send",
							"receive", "on-message");
				});
	}

	@Test
	@Ignore("flakey")
	public void tracesListener_annotationMessageListener() {
		this.contextRunner.withUserConfiguration(AnnotationJmsListenerConfiguration.class)
				.run(ctx -> {
					clearSpans(ctx);
					ctx.getBean(JmsTemplate.class).convertAndSend("myQueue", "foo");

					Callable<Span> takeSpan = ctx.getBean("takeSpan", Callable.class);
					List<Span> trace = Arrays.asList(takeSpan.call(), takeSpan.call(),
							takeSpan.call());

					assertThat(trace).allSatisfy(s -> assertThat(s.traceId())
							.isEqualTo(trace.get(0).traceId()));
					assertThat(trace).isNotNull().extracting(Span::name)
							.containsExactlyInAnyOrder("send", "receive", "on-message");
				});
	}

	@Test
	public void tracesListener_jcaMessageListener() {
		this.contextRunner.withUserConfiguration(JcaJmsListenerConfiguration.class)
				.run(ctx -> {
					clearSpans(ctx);
					ctx.getBean(JmsTemplate.class).convertAndSend("myQueue", "foo");

					Callable<Span> takeSpan = ctx.getBean("takeSpan", Callable.class);
					List<Span> trace = Arrays.asList(takeSpan.call(), takeSpan.call(),
							takeSpan.call());

					assertThat(trace).allSatisfy(s -> assertThat(s.traceId())
							.isEqualTo(trace.get(0).traceId()));
					assertThat(trace).isNotNull().extracting(Span::name)
							.containsExactlyInAnyOrder("send", "receive", "on-message");
				});
	}

	@AutoConfigureBefore(ActiveMQAutoConfiguration.class)
	static class XAConfiguration {

		@Bean
		XAConnectionFactoryWrapper xaConnectionFactoryWrapper() {
			return connectionFactory -> (ConnectionFactory) connectionFactory;
		}

	}

	@Configuration
	@EnableJms
	static class SimpleJmsListenerConfiguration implements JmsListenerConfigurer {

		private static final Log log = LogFactory.getLog(AnnotationJmsListenerConfiguration.class);

		@Autowired
		CurrentTraceContext current;

		@Override
		public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
			SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
			endpoint.setId("myCustomEndpointId");
			endpoint.setDestination("myQueue");
			endpoint.setMessageListener(simpleMessageListener(this.current));
			registrar.registerEndpoint(endpoint);
		}

		@Bean
		MessageListener simpleMessageListener(CurrentTraceContext current) {
			return message -> {
				log.info("Got message");
				// Didn't restart the trace
				assertThat(current.get()).isNotNull()
						.extracting(TraceContext::parentIdAsLong).isNotEqualTo(0L);
			};
		}

	}

	@Configuration
	@EnableJms
	static class AnnotationJmsListenerConfiguration {

		private static final Log log = LogFactory.getLog(AnnotationJmsListenerConfiguration.class);

		@Autowired
		CurrentTraceContext current;

		@JmsListener(destination = "myQueue")
		public void onMessage() {
			log.info("Got message!");
			assertThat(this.current.get()).isNotNull()
					.extracting(TraceContext::parentIdAsLong).isNotEqualTo(0L);
		}

	}

	@Configuration
	static class JcaJmsListenerConfiguration {

		@Autowired
		CurrentTraceContext current;

		@Bean
		ResourceAdapterFactoryBean resourceAdapter() {
			ResourceAdapterFactoryBean resourceAdapter = new ResourceAdapterFactoryBean();
			ActiveMQResourceAdapter real = new ActiveMQResourceAdapter();
			real.setServerUrl("vm://localhost?broker.persistent=false");
			resourceAdapter.setResourceAdapter(real);
			resourceAdapter.setWorkManager(new SimpleTaskWorkManager());
			return resourceAdapter;
		}

		@Bean
		MessageListener simpleMessageListener(CurrentTraceContext current) {
			return message -> {
				// Didn't restart the trace
				assertThat(current.get()).isNotNull()
						.extracting(TraceContext::parentIdAsLong).isNotEqualTo(0L);
			};
		}

		@Bean
		JmsMessageEndpointManager endpointManager(ResourceAdapter resourceAdapter,
				MessageListener simpleMessageListener) {
			JmsMessageEndpointManager endpointManager = new JmsMessageEndpointManager();
			endpointManager.setResourceAdapter(resourceAdapter);

			ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
			spec.setUseJndi(false);
			spec.setDestinationType("javax.jms.Queue");
			spec.setDestination("myQueue");

			endpointManager.setActivationSpec(spec);
			endpointManager.setMessageListener(simpleMessageListener);
			return endpointManager;
		}

	}

}

@Configuration
@EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
class JmsTestTracingConfiguration {

	static final String CONTEXT_LEAK = "context.leak";

	/**
	 * When testing servers or asynchronous clients, spans are reported on a worker
	 * thread. In order to read them on the main thread, we use a concurrent queue. As
	 * some implementations report after a response is sent, we use a blocking queue to
	 * prevent race conditions in tests.
	 */
	BlockingQueue<Span> spans = new LinkedBlockingQueue<>();

	void clearSpan() {
		this.spans.clear();
	}

	/**
	 * Call this to block until a span was reported.
	 * @return span from queue
	 */
	@Bean
	Callable<Span> takeSpan() {
		return () -> {
			Span result = this.spans.poll(3, TimeUnit.SECONDS);
			assertThat(result).withFailMessage("Span was not reported").isNotNull();
			assertThat(result.annotations()).extracting(Annotation::value)
					.doesNotContain(CONTEXT_LEAK);
			return result;
		};
	}

	@Bean
	Tracing tracing(CurrentTraceContext currentTraceContext) {
		return Tracing.newBuilder().spanReporter(s -> {
			// make sure the context was cleared prior to finish.. no leaks!
			TraceContext current = currentTraceContext.get();
			boolean contextLeak = false;
			if (current != null) {
				// add annotation in addition to throwing, in case we are off the main
				// thread
				if (HexCodec.toLowerHex(current.spanId()).equals(s.id())) {
					s = s.toBuilder().addAnnotation(s.timestampAsLong(), CONTEXT_LEAK)
							.build();
					contextLeak = true;
				}
			}
			this.spans.add(s);
			// throw so that we can see the path to the code that leaked the context
			if (contextLeak) {
				throw new AssertionError(
						CONTEXT_LEAK + " on " + Thread.currentThread().getName());
			}
		}).currentTraceContext(currentTraceContext).build();
	}

}
