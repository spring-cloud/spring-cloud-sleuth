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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.cloud.function.context.catalog.FunctionAroundWrapper;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable tracing via Spring Cloud Function.
 *
 * @author Oleg Zhurakousky
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.function.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracing.class)
@ConditionalOnClass({ Tracer.class, FunctionAroundWrapper.class })
@AutoConfigureAfter(TraceAutoConfiguration.class)
class TraceFunctionAutoConfiguration {

	@Bean
	TraceFunctionAroundWrapper traceFunctionAroundWrapper(Environment environment, Tracing tracing) {
		return new TraceFunctionAroundWrapper(environment, tracing);
	}

}

class TraceFunctionAroundWrapper extends FunctionAroundWrapper
		implements ApplicationListener<RefreshScopeRefreshedEvent> {

	private static final Log log = LogFactory.getLog(TraceFunctionAroundWrapper.class);

	private final Environment environment;

	private final Tracing tracing;

	final Map<String, String> functionToDestinationCache = new ConcurrentHashMap<>();

	TraceFunctionAroundWrapper(Environment environment, Tracing tracing) {
		this.environment = environment;
		this.tracing = tracing;
	}

	@Override
	protected Object doApply(Message<byte[]> message, SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		TraceMessageHandler traceMessageHandler = TraceMessageHandler.forNonSpringIntegration(this.tracing);
		if (log.isDebugEnabled()) {
			log.debug("Will retrieve the tracing headers from the message");
		}
		MessageAndSpans wrappedInputMessage = traceMessageHandler.wrapInputMessage(message,
				inputDestination(targetFunction));
		if (log.isDebugEnabled()) {
			log.debug("Wrapped input msg " + wrappedInputMessage);
		}
		Tracer tracer = this.tracing.tracer();
		Object result;
		Throwable throwable = null;
		try (Tracer.SpanInScope ws = tracer.withSpanInScope(wrappedInputMessage.childSpan.start())) {
			result = targetFunction.apply(wrappedInputMessage.msg);
		}
		catch (Exception e) {
			throwable = e;
			throw e;
		}
		finally {
			traceMessageHandler.afterMessageHandled(wrappedInputMessage.childSpan, throwable);
		}
		if (result == null) {
			if (log.isDebugEnabled()) {
				log.debug("Returned message is null - we have a consumer");
			}
			return null;
		}
		Message msgResult = toMessage(result);
		MessageAndSpan wrappedOutputMessage = traceMessageHandler.wrapOutputMessage(msgResult,
				TraceContextOrSamplingFlags.create(wrappedInputMessage.parentSpan.context()),
				outputDestination(targetFunction));
		if (log.isDebugEnabled()) {
			log.debug("Wrapped output msg " + wrappedOutputMessage);
		}
		traceMessageHandler.afterMessageHandled(wrappedOutputMessage.span, null);
		return wrappedOutputMessage.msg;
	}

	private Message toMessage(Object result) {
		if (!(result instanceof Message)) {
			return MessageBuilder.withPayload(result).build();
		}
		return (Message) result;
	}

	private String inputDestination(SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		String functionDefinition = targetFunction.getFunctionDefinition();
		return this.functionToDestinationCache.computeIfAbsent(functionDefinition,
				s -> this.environment.getProperty("spring.cloud.stream.bindings." + s + "-in-0.destination", s));
	}

	private String outputDestination(SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		String functionDefinition = targetFunction.getFunctionDefinition();
		return functionToDestinationCache.computeIfAbsent(functionDefinition,
				s -> this.environment.getProperty("spring.cloud.stream.bindings." + s + "-out-0.destination", s));
	}

	@Override
	public void onApplicationEvent(RefreshScopeRefreshedEvent event) {
		if (log.isDebugEnabled()) {
			log.debug("Context refreshed, will reset the cache");
		}
		this.functionToDestinationCache.clear();
	}

}
