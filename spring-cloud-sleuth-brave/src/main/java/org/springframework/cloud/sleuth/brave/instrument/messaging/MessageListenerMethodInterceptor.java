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

package org.springframework.cloud.sleuth.brave.instrument.messaging;

import brave.Span;
import brave.Tracer;
import brave.kafka.clients.KafkaTracing;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.springframework.kafka.listener.MessageListener;

class MessageListenerMethodInterceptor<T extends MessageListener> implements MethodInterceptor {

	private static final Log log = LogFactory.getLog(MessageListenerMethodInterceptor.class);

	private final KafkaTracing kafkaTracing;

	private final Tracer tracer;

	MessageListenerMethodInterceptor(KafkaTracing kafkaTracing, Tracer tracer) {
		this.kafkaTracing = kafkaTracing;
		this.tracer = tracer;
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		if (!"onMessage".equals(invocation.getMethod().getName())) {
			return invocation.proceed();
		}
		Object[] arguments = invocation.getArguments();
		Object record = record(arguments);
		if (record == null) {
			return invocation.proceed();
		}
		if (log.isDebugEnabled()) {
			log.debug("Wrapping onMessage call");
		}
		Span span = this.kafkaTracing.nextSpan((ConsumerRecord<?, ?>) record).name("on-message").start();
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			return invocation.proceed();
		}
		catch (RuntimeException | Error e) {
			String message = e.getMessage();
			if (message == null) {
				message = e.getClass().getSimpleName();
			}
			span.tag("error", message);
			throw e;
		}
		finally {
			span.finish();
		}
	}

	private Object record(Object[] arguments) {
		for (Object object : arguments) {
			if (object instanceof ConsumerRecord) {
				return object;
			}
		}
		return null;
	}

}
