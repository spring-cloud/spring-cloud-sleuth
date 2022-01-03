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

package org.springframework.cloud.sleuth.instrument.kafka;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.kafka.listener.MessageListener;

class TracingMessageListenerMethodInterceptor<T extends MessageListener> implements MethodInterceptor {

	private static final Log log = LogFactory.getLog(TracingMessageListenerMethodInterceptor.class);

	private final Tracer tracer;

	private final Propagator propagator;

	private final Propagator.Getter<ConsumerRecord<?, ?>> extractor;

	TracingMessageListenerMethodInterceptor(Tracer tracer, Propagator propagator,
			Propagator.Getter<ConsumerRecord<?, ?>> extractor) {
		this.tracer = tracer;
		this.propagator = propagator;
		this.extractor = extractor;
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
		Span span = KafkaTracingUtils.buildSpan(SleuthKafkaSpan.KAFKA_ON_MESSAGE_SPAN, (ConsumerRecord<?, ?>) record,
				this.propagator, this.extractor);
		try (Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
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
			span.end();
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
