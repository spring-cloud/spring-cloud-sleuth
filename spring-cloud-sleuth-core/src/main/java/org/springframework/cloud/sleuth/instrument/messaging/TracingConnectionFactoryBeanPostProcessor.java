/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.messaging;

import java.lang.reflect.Field;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XAJMSContext;

import brave.Span;
import brave.jms.JmsTracing;
import brave.propagation.CurrentTraceContext;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.config.MethodJmsListenerEndpoint;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.jms.listener.endpoint.JmsMessageEndpointManager;
import org.springframework.lang.Nullable;

/**
 * {@link BeanPostProcessor} wrapping around JMS {@link ConnectionFactory}
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
		// We check XA first in case the ConnectionFactory also implements
		// XAConnectionFactory
		if (bean instanceof XAConnectionFactory) {
			return new LazyXAConnectionFactory(this.beanFactory,
					(XAConnectionFactory) bean);
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
		return this.jmsTracing = this.beanFactory.getBean(JmsTracing.class);
	}

	private XAConnectionFactory wrappedDelegate() {
		if (this.wrappedDelegate != null) {
			return this.wrappedDelegate;
		}
		return this.wrappedDelegate = jmsTracing().xaConnectionFactory(this.delegate);
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
		return this.jmsTracing = this.beanFactory.getBean(JmsTracing.class);
	}

	private ConnectionFactory wrappedDelegate() {
		if (this.wrappedDelegate != null) {
			return this.wrappedDelegate;
		}
		return this.wrappedDelegate = jmsTracing().connectionFactory(this.delegate);
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
		return this.jmsTracing = this.beanFactory.getBean(JmsTracing.class);
	}

	private MessageListener wrappedDelegate() {
		// Adds a consumer span as we have no visibility into JCA's implementation of
		// messaging
		return jmsTracing().messageListener(this.delegate, true);
	}

}

/**
 * This ensures listeners end up continuing the trace from
 * {@link MessageConsumer#receive()}
 */
class TracingJmsListenerEndpointRegistry extends JmsListenerEndpointRegistry {

	final JmsTracing jmsTracing;

	final CurrentTraceContext current;

	// Not all state can be copied without using reflection
	final Field messageHandlerMethodFactoryField;

	final Field embeddedValueResolverField;

	TracingJmsListenerEndpointRegistry(JmsTracing jmsTracing,
			CurrentTraceContext current) {
		this.jmsTracing = jmsTracing;
		this.current = current;
		this.messageHandlerMethodFactoryField = tryField("messageHandlerMethodFactory");
		this.embeddedValueResolverField = tryField("embeddedValueResolver");
	}

	@Nullable
	static Field tryField(String name) {
		try {
			Field field = MethodJmsListenerEndpoint.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		}
		catch (NoSuchFieldException e) {
			return null;
		}
	}

	@Nullable
	static <T> T get(Object object, Field field) throws IllegalAccessException {
		return (T) field.get(object);
	}

	@Override
	public void registerListenerContainer(JmsListenerEndpoint endpoint,
			JmsListenerContainerFactory<?> factory, boolean startImmediately) {
		if (endpoint instanceof MethodJmsListenerEndpoint) {
			endpoint = trace((MethodJmsListenerEndpoint) endpoint);
		}
		else if (endpoint instanceof SimpleJmsListenerEndpoint) {
			endpoint = trace((SimpleJmsListenerEndpoint) endpoint);
		}
		super.registerListenerContainer(endpoint, factory, startImmediately);
	}

	/**
	 * This wraps the {@link SimpleJmsListenerEndpoint#getMessageListener()} delegate in a
	 * new span.
	 */
	SimpleJmsListenerEndpoint trace(SimpleJmsListenerEndpoint source) {
		MessageListener delegate = source.getMessageListener();
		if (delegate == null)
			return source;
		source.setMessageListener(this.jmsTracing.messageListener(delegate, false));
		return source;
	}

	/**
	 * It would be better to trace by wrapping, but
	 * {@link MethodJmsListenerEndpoint#createMessageListenerInstance()}, is protected so
	 * we can't call it from outside code. In other words, a forwarding pattern can't be
	 * used. Instead, we copy state from the input.
	 * <p>
	 * NOTE: As {@linkplain MethodJmsListenerEndpoint} is neither final, nor effectively
	 * final. For this reason we can't ensure copying will get all state. For example, a
	 * subtype could hold state we aren't aware of, or change behavior. We can consider
	 * checking that input is not a subtype, and most conservatively leaving unknown
	 * subtypes untraced.
	 */
	MethodJmsListenerEndpoint trace(MethodJmsListenerEndpoint source) {
		// Skip out rather than incompletely copying the source
		if (this.messageHandlerMethodFactoryField == null
				|| this.embeddedValueResolverField == null) {
			return source;
		}

		// We want the stock implementation, except we want to wrap the message listener
		// in a new span
		MethodJmsListenerEndpoint dest = new MethodJmsListenerEndpoint() {
			@Override
			protected MessagingMessageListenerAdapter createMessageListenerInstance() {
				return new TracingMessagingMessageListenerAdapter(
						TracingJmsListenerEndpointRegistry.this.jmsTracing,
						TracingJmsListenerEndpointRegistry.this.current);
			}
		};

		// set state from AbstractJmsListenerEndpoint
		dest.setId(source.getId());
		dest.setDestination(source.getDestination());
		dest.setSubscription(source.getSubscription());
		dest.setSelector(source.getSelector());
		dest.setConcurrency(source.getConcurrency());

		// set state from MethodJmsListenerEndpoint
		dest.setBean(source.getBean());
		dest.setMethod(source.getMethod());
		dest.setMostSpecificMethod(source.getMostSpecificMethod());

		try {
			dest.setMessageHandlerMethodFactory(
					get(source, this.messageHandlerMethodFactoryField));
			dest.setEmbeddedValueResolver(get(source, this.embeddedValueResolverField));
		}
		catch (IllegalAccessException e) {
			return source; // skip out rather than incompletely copying the source
		}
		return dest;
	}

}

/**
 * This wraps the message listener in a child span
 */
final class TracingMessagingMessageListenerAdapter
		extends MessagingMessageListenerAdapter {

	final JmsTracing jmsTracing;

	final CurrentTraceContext current;

	TracingMessagingMessageListenerAdapter(JmsTracing jmsTracing,
			CurrentTraceContext current) {
		this.jmsTracing = jmsTracing;
		this.current = current;
	}

	@Override
	public void onMessage(Message message, Session session) throws JMSException {
		Span span = this.jmsTracing.nextSpan(message).name("on-message").start();
		try (CurrentTraceContext.Scope ws = this.current.newScope(span.context())) {
			super.onMessage(message, session);
		}
		catch (JMSException | RuntimeException | Error e) {
			span.error(e);
			throw e;
		}
		finally {
			span.finish();
		}
	}

}