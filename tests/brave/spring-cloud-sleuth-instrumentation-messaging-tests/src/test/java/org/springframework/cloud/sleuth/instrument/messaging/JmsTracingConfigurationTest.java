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

package org.springframework.cloud.sleuth.instrument.messaging;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.jms.XAConnectionFactoryWrapper;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;

import static org.assertj.core.api.Assertions.assertThat;

// inspired by org.springframework.boot.autoconfigure.jms.JmsAutoConfigurationTests

/**
 * @author Adrian Cole
 */
public class JmsTracingConfigurationTest {

	final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(AutoConfigurations
			.of(JmsTestTracingConfiguration.class, XAConfiguration.class, SimpleJmsListenerConfiguration.class));

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

	static void checkTopicConnection(AssertableApplicationContext ctx) throws JMSException {
		// Not using try-with-resources as that doesn't exist in JMS 1.1
		TopicConnection con = ctx.getBean(TopicConnectionFactory.class).createTopicConnection();
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
			checkConnection(ctx);
			checkXAConnection(ctx);
		});
	}

	@Test
	public void tracesTopicConnectionFactories() {
		this.contextRunner.withUserConfiguration(XAConfiguration.class).run(ctx -> {
			checkConnection(ctx);
			checkTopicConnection(ctx);
		});
	}

	@AutoConfigureBefore(ActiveMQAutoConfiguration.class)
	static class XAConfiguration {

		@Bean
		XAConnectionFactoryWrapper xaConnectionFactoryWrapper() {
			return connectionFactory -> (ConnectionFactory) connectionFactory;
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableJms
	static class SimpleJmsListenerConfiguration implements JmsListenerConfigurer {

		private static final Log log = LogFactory.getLog(SimpleJmsListenerConfiguration.class);

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
				assertThat(current.get()).isNotNull().extracting(TraceContext::parentIdAsLong).isNotEqualTo(0L);
			};
		}

	}

}

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = { KafkaAutoConfiguration.class, MongoAutoConfiguration.class, QuartzAutoConfiguration.class })
class JmsTestTracingConfiguration {

}
