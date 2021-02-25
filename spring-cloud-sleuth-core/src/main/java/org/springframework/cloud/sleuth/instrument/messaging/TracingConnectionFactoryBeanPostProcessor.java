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

package org.springframework.cloud.sleuth.instrument.messaging;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XAJMSContext;

import brave.jms.JmsTracing;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.listener.endpoint.JmsMessageEndpointManager;

/**
 * {@link BeanPostProcessor} wrapping around JMS {@link ConnectionFactory}.
 *
 * @author Adrian Cole
 * @since 2.1.0
 */
class TracingConnectionFactoryBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	TracingConnectionFactoryBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		// Wrap the caching connection factories instead of its target, because it catches
		// callbacks
		// such as ExceptionListener. If we don't wrap, cached callbacks like this won't
		// be traced.
		if (bean instanceof CachingConnectionFactory) {
			return new LazyConnectionFactory(this.beanFactory,
					(CachingConnectionFactory) bean);
		}
		if (bean instanceof JmsMessageEndpointManager) {
			JmsMessageEndpointManager manager = (JmsMessageEndpointManager) bean;
			MessageListener listener = manager.getMessageListener();
			if (listener != null) {
				manager.setMessageListener(
						new LazyMessageListener(this.beanFactory, listener));
			}
			return bean;
		}
		if (bean instanceof XAConnectionFactory && bean instanceof ConnectionFactory) {
			return new LazyConnectionAndXaConnectionFactory(this.beanFactory,
					(ConnectionFactory) bean, (XAConnectionFactory) bean);
		}
		// We check XA first in case the ConnectionFactory also implements
		// XAConnectionFactory
		else if (bean instanceof XAConnectionFactory) {
			return new LazyXAConnectionFactory(this.beanFactory,
					(XAConnectionFactory) bean);
		}
		else if (bean instanceof TopicConnectionFactory) {
			return new LazyTopicConnectionFactory(this.beanFactory,
					(TopicConnectionFactory) bean);
		}
		else if (bean instanceof ConnectionFactory) {
			return new LazyConnectionFactory(this.beanFactory, (ConnectionFactory) bean);
		}
		return bean;
	}

}

class LazyXAConnectionFactory implements XAConnectionFactory {

	private final BeanFactory beanFactory;

	private final XAConnectionFactory delegate;

	private JmsTracing jmsTracing;

	private XAConnectionFactory wrappedDelegate;

	LazyXAConnectionFactory(BeanFactory beanFactory, XAConnectionFactory delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override
	public XAConnection createXAConnection() throws JMSException {
		return wrappedDelegate().createXAConnection();
	}

	@Override
	public XAConnection createXAConnection(String s, String s1) throws JMSException {
		return wrappedDelegate().createXAConnection(s, s1);
	}

	@Override
	public XAJMSContext createXAContext() {
		return wrappedDelegate().createXAContext();
	}

	@Override
	public XAJMSContext createXAContext(String s, String s1) {
		return wrappedDelegate().createXAContext(s, s1);
	}

	private JmsTracing jmsTracing() {
		if (this.jmsTracing != null) {
			return this.jmsTracing;
		}
		this.jmsTracing = this.beanFactory.getBean(JmsTracing.class);
		return this.jmsTracing;
	}

	private XAConnectionFactory wrappedDelegate() {
		if (this.wrappedDelegate != null) {
			return this.wrappedDelegate;
		}
		this.wrappedDelegate = jmsTracing().xaConnectionFactory(this.delegate);
		return this.wrappedDelegate;
	}

}

class LazyTopicConnectionFactory implements TopicConnectionFactory {

	private final BeanFactory beanFactory;

	private final TopicConnectionFactory delegate;

	private final LazyConnectionFactory factory;

	private JmsTracing jmsTracing;

	LazyTopicConnectionFactory(BeanFactory beanFactory, TopicConnectionFactory delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.factory = new LazyConnectionFactory(beanFactory, delegate);
	}

	@Override
	public TopicConnection createTopicConnection() throws JMSException {
		return jmsTracing().topicConnection(this.delegate.createTopicConnection());
	}

	@Override
	public TopicConnection createTopicConnection(String s, String s1)
			throws JMSException {
		return jmsTracing().topicConnection(this.delegate.createTopicConnection(s, s1));
	}

	@Override
	public Connection createConnection() throws JMSException {
		return this.factory.createConnection();
	}

	@Override
	public Connection createConnection(String s, String s1) throws JMSException {
		return this.factory.createConnection(s, s1);
	}

	@Override
	public JMSContext createContext() {
		return this.factory.createContext();
	}

	@Override
	public JMSContext createContext(String s, String s1) {
		return this.factory.createContext(s, s1);
	}

	@Override
	public JMSContext createContext(String s, String s1, int i) {
		return this.factory.createContext(s, s1, i);
	}

	@Override
	public JMSContext createContext(int i) {
		return this.factory.createContext(i);
	}

	private JmsTracing jmsTracing() {
		if (this.jmsTracing != null) {
			return this.jmsTracing;
		}
		this.jmsTracing = this.beanFactory.getBean(JmsTracing.class);
		return this.jmsTracing;
	}

}

class LazyConnectionFactory implements ConnectionFactory {

	private final BeanFactory beanFactory;

	private final ConnectionFactory delegate;

	private JmsTracing jmsTracing;

	private ConnectionFactory wrappedDelegate;

	LazyConnectionFactory(BeanFactory beanFactory, ConnectionFactory delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override
	public Connection createConnection() throws JMSException {
		return wrappedDelegate().createConnection();
	}

	@Override
	public Connection createConnection(String s, String s1) throws JMSException {
		return wrappedDelegate().createConnection(s, s1);
	}

	@Override
	public JMSContext createContext() {
		return wrappedDelegate().createContext();
	}

	@Override
	public JMSContext createContext(String s, String s1) {
		return wrappedDelegate().createContext(s, s1);
	}

	@Override
	public JMSContext createContext(String s, String s1, int i) {
		return wrappedDelegate().createContext(s, s1, i);
	}

	@Override
	public JMSContext createContext(int i) {
		return wrappedDelegate().createContext(i);
	}

	private JmsTracing jmsTracing() {
		if (this.jmsTracing != null) {
			return this.jmsTracing;
		}
		this.jmsTracing = this.beanFactory.getBean(JmsTracing.class);
		return this.jmsTracing;
	}

	private ConnectionFactory wrappedDelegate() {
		if (this.wrappedDelegate != null) {
			return this.wrappedDelegate;
		}
		this.wrappedDelegate = jmsTracing().connectionFactory(this.delegate);
		return this.wrappedDelegate;
	}

}

class LazyConnectionAndXaConnectionFactory
		implements ConnectionFactory, XAConnectionFactory {

	private final ConnectionFactory connectionFactoryDelegate;

	private final XAConnectionFactory xaConnectionFactoryDelegate;

	LazyConnectionAndXaConnectionFactory(BeanFactory beanFactory,
			ConnectionFactory connectionFactoryDelegate,
			XAConnectionFactory xaConnectionFactoryDelegate) {
		this.connectionFactoryDelegate = new LazyConnectionFactory(beanFactory,
				connectionFactoryDelegate);
		this.xaConnectionFactoryDelegate = new LazyXAConnectionFactory(beanFactory,
				xaConnectionFactoryDelegate);
	}

	@Override
	public Connection createConnection() throws JMSException {
		return this.connectionFactoryDelegate.createConnection();
	}

	@Override
	public Connection createConnection(String userName, String password)
			throws JMSException {
		return this.connectionFactoryDelegate.createConnection(userName, password);
	}

	@Override
	public JMSContext createContext() {
		return this.connectionFactoryDelegate.createContext();
	}

	@Override
	public JMSContext createContext(String userName, String password) {
		return this.connectionFactoryDelegate.createContext(userName, password);
	}

	@Override
	public JMSContext createContext(String userName, String password, int sessionMode) {
		return this.connectionFactoryDelegate.createContext(userName, password,
				sessionMode);
	}

	@Override
	public JMSContext createContext(int sessionMode) {
		return this.connectionFactoryDelegate.createContext(sessionMode);
	}

	@Override
	public XAConnection createXAConnection() throws JMSException {
		return this.xaConnectionFactoryDelegate.createXAConnection();
	}

	@Override
	public XAConnection createXAConnection(String userName, String password)
			throws JMSException {
		return this.xaConnectionFactoryDelegate.createXAConnection(userName, password);
	}

	@Override
	public XAJMSContext createXAContext() {
		return this.xaConnectionFactoryDelegate.createXAContext();
	}

	@Override
	public XAJMSContext createXAContext(String userName, String password) {
		return this.xaConnectionFactoryDelegate.createXAContext(userName, password);
	}

}

class LazyMessageListener implements MessageListener {

	private final BeanFactory beanFactory;

	private final MessageListener delegate;

	private JmsTracing jmsTracing;

	LazyMessageListener(BeanFactory beanFactory, MessageListener delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override
	public void onMessage(Message message) {
		wrappedDelegate().onMessage(message);
	}

	private JmsTracing jmsTracing() {
		if (this.jmsTracing != null) {
			return this.jmsTracing;
		}
		this.jmsTracing = this.beanFactory.getBean(JmsTracing.class);
		return this.jmsTracing;
	}

	private MessageListener wrappedDelegate() {
		// Adds a consumer span as we have no visibility into JCA's implementation of
		// messaging
		return jmsTracing().messageListener(this.delegate, true);
	}

}
