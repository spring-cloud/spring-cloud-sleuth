/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.jms.config;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;

import brave.Span;
import brave.jms.JmsTracing;
import brave.propagation.CurrentTraceContext;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.jms.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.lang.Nullable;

/**
 * This ensures listeners end up continuing the trace from
 * {@link MessageConsumer#receive()}.
 *
 * Internal class, do not use. It's API can change at anytime.
 *
 * @author Marcin Grzejszczak
 */
public final class TracingJmsListenerEndpointRegistry
		extends JmsListenerEndpointRegistry {

	final BeanFactory beanFactory;

	final JmsListenerEndpointRegistry delegate;

	JmsTracing jmsTracing;

	CurrentTraceContext currentTraceContext;

	// Not all state can be copied without using reflection
	final Field messageHandlerMethodFactoryField;

	final Field embeddedValueResolverField;

	public TracingJmsListenerEndpointRegistry(JmsListenerEndpointRegistry registry,
			BeanFactory beanFactory) {
		this.delegate = registry;
		this.beanFactory = beanFactory;
		this.messageHandlerMethodFactoryField = tryField("messageHandlerMethodFactory");
		this.embeddedValueResolverField = tryField("embeddedValueResolver");
	}

	private JmsTracing jmsTracing() {
		if (this.jmsTracing == null) {
			this.jmsTracing = this.beanFactory.getBean(JmsTracing.class);
		}
		return this.jmsTracing;
	}

	private CurrentTraceContext currentTraceContext() {
		if (this.currentTraceContext == null) {
			this.currentTraceContext = this.beanFactory
					.getBean(CurrentTraceContext.class);
		}
		return this.currentTraceContext;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.delegate.setApplicationContext(applicationContext);
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		this.delegate.onApplicationEvent(event);
	}

	@Override
	public MessageListenerContainer getListenerContainer(String id) {
		return this.delegate.getListenerContainer(id);
	}

	@Override
	public Set<String> getListenerContainerIds() {
		return this.delegate.getListenerContainerIds();
	}

	@Override
	public Collection<MessageListenerContainer> getListenerContainers() {
		return this.delegate.getListenerContainers();
	}

	@Override
	public void registerListenerContainer(JmsListenerEndpoint endpoint,
			JmsListenerContainerFactory<?> factory) {
		this.delegate.registerListenerContainer(endpoint, factory);
	}

	@Override
	protected MessageListenerContainer createListenerContainer(
			JmsListenerEndpoint endpoint, JmsListenerContainerFactory<?> factory) {
		return this.delegate.createListenerContainer(endpoint, factory);
	}

	@Override
	public int getPhase() {
		return this.delegate.getPhase();
	}

	@Override
	public void start() {
		this.delegate.start();
	}

	@Override
	public void stop() {
		this.delegate.stop();
	}

	@Override
	public void stop(Runnable callback) {
		this.delegate.stop(callback);
	}

	@Override
	public boolean isRunning() {
		return this.delegate.isRunning();
	}

	@Override
	public void destroy() {
		this.delegate.destroy();
	}

	@Override
	public boolean isAutoStartup() {
		return this.delegate.isAutoStartup();
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
		this.delegate.registerListenerContainer(endpoint, factory, startImmediately);
	}

	/**
	 * This wraps the {@link SimpleJmsListenerEndpoint#getMessageListener()} delegate in a
	 * new span.
	 * @param source jms endpoint
	 * @return wrapped endpoint
	 */
	SimpleJmsListenerEndpoint trace(SimpleJmsListenerEndpoint source) {
		MessageListener delegate = source.getMessageListener();
		if (delegate == null) {
			return source;
		}
		source.setMessageListener(jmsTracing().messageListener(delegate, false));
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
	 * @param source jms endpoint
	 * @return wrapped endpoint
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
				return new TracingMessagingMessageListenerAdapter(jmsTracing(),
						currentTraceContext());
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
 * This wraps the message listener in a child span.
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
