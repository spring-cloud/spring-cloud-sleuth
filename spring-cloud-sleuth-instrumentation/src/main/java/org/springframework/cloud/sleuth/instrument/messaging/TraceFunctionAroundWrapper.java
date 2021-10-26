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

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.cloud.function.context.catalog.FunctionAroundWrapper;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Trace representation of a {@link FunctionAroundWrapper}.
 *
 * @author Marcin Grzejszczak
 * @author Oleg Zhurakousky
 * @since 3.0.0
 */
public class TraceFunctionAroundWrapper extends FunctionAroundWrapper
		implements ApplicationListener<RefreshScopeRefreshedEvent> {

	private static final Log log = LogFactory.getLog(TraceFunctionAroundWrapper.class);

	private final Environment environment;

	private final Tracer tracer;

	private final Propagator propagator;

	private final Propagator.Setter<MessageHeaderAccessor> injector;

	private final Propagator.Getter<MessageHeaderAccessor> extractor;

	private final TraceMessageHandler traceMessageHandler;

	private final List<FunctionMessageSpanCustomizer> customizers;

	final Map<String, String> functionToDestinationCache = new ConcurrentHashMap<>();

	public TraceFunctionAroundWrapper(Environment environment, Tracer tracer, Propagator propagator,
			Propagator.Setter<MessageHeaderAccessor> injector, Propagator.Getter<MessageHeaderAccessor> extractor) {
		this(environment, tracer, propagator, injector, extractor, Collections.emptyList());
	}

	public TraceFunctionAroundWrapper(Environment environment, Tracer tracer, Propagator propagator,
			Propagator.Setter<MessageHeaderAccessor> injector, Propagator.Getter<MessageHeaderAccessor> extractor,
			List<FunctionMessageSpanCustomizer> customizers) {
		this.environment = environment;
		this.tracer = tracer;
		this.propagator = propagator;
		this.injector = injector;
		this.extractor = extractor;
		this.customizers = customizers;
		this.traceMessageHandler = TraceMessageHandler.forNonSpringIntegration(this.tracer, this.propagator,
				this.injector, this.extractor, this.customizers);
	}

	@Override
	protected Object doApply(Object message, SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		if (FunctionTypeUtils.isCollectionOfMessage(targetFunction.getOutputType())) {
			return targetFunction.apply(message); // no instrumentation
		}
		else if (targetFunction.isInputTypePublisher() || targetFunction.isOutputTypePublisher()) {
			return reactorStream((Publisher) message, targetFunction);
		}
		return nonReactorStream((Message<byte[]>) message, targetFunction);
	}

	private Object reactorStream(Publisher messageStream,
			SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		if (messageStream == null && targetFunction.isSupplier()) { // Supplier
			return reactorStreamSupplier(messageStream, targetFunction);
		}
		Type itemType = FunctionTypeUtils.getGenericType(targetFunction.getInputType());
		Class<?> itemTypeClass = FunctionTypeUtils.getRawType(itemType);
		if (!itemTypeClass.equals(Message.class)) {
			if (log.isDebugEnabled()) {
				log.debug("Target function [" + targetFunction.getFunctionDefinition() + "] has raw input type ["
						+ itemType + "] and should be [" + Message.class + "]. Will not wrap it.");
				return targetFunction.apply(messageStream);
			}
		}
		Publisher<Message> messagePublisher = messageStream;
		if (FunctionTypeUtils.isMono(targetFunction.getInputType())) {
			return reactorMonoStream(targetFunction, messagePublisher);
		}
		return reactorFluxStream(targetFunction, messagePublisher);
	}

	private Object reactorMonoStream(SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction,
			Publisher<Message> messagePublisher) {
		if (log.isDebugEnabled()) {
			log.debug("Will instrument a stream Mono function");
		}
		Mono<Message> mono = Mono.from(messagePublisher)
				// ensure there are no previous spans
				.doOnNext(m -> tracer.withSpan(null))
				.map(msg -> this.traceMessageHandler.wrapInputMessage(msg,
						inputDestination(targetFunction.getFunctionDefinition())))
				.flatMap(msg -> Mono.deferContextual(ctx -> {
					MessageAndSpansAndScope messageAndSpansAndScope = ctx.get(MessageAndSpansAndScope.class);
					messageAndSpansAndScope.messageAndSpans = msg;
					messageAndSpansAndScope.span = msg.childSpan;
					setNameAndTag(targetFunction, msg.childSpan);
					messageAndSpansAndScope.scope = tracer.withSpan(msg.childSpan);
					return Mono.just(msg.msg);
				}));
		if (targetFunction.isConsumer()) {
			return targetFunction.apply(reactorStreamConsumer(mono));
		}
		final Mono<Message> function = ((Mono<Message>) targetFunction.apply(mono));
		return Mono.deferContextual(contextView -> {
			MessageAndSpansAndScope msg = contextView.get(MessageAndSpansAndScope.class);
			return function.doOnNext(message -> {
				msg.end();
				msg.handle();
			}).map(msgResult -> {
				MessageAndSpan messageAndSpan = traceMessageHandler.wrapOutputMessage(msgResult,
						msg.messageAndSpans.parentSpan, outputDestination(targetFunction.getFunctionDefinition()));
				traceMessageHandler.afterMessageHandled(messageAndSpan.span, null);
				return messageAndSpan.msg;
			})
					// TODO: Fix me when this is resolved in Reactor
					// .doOnSubscribe(__ -> scope.close())
					.doOnError(msg::error).doFinally(signalType -> {
						if (!msg.isHandled()) {
							msg.end();
						}
					});
		}).contextWrite(contextView -> contextView.put(MessageAndSpansAndScope.class, new MessageAndSpansAndScope()));
	}

	private Object reactorFluxStream(SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction,
			Publisher<Message> messagePublisher) {
		if (log.isDebugEnabled()) {
			log.debug("Will instrument a stream Flux function");
		}
		Flux<Message> flux = Flux.from(messagePublisher)
				// ensure there are no previous spans
				.doOnNext(m -> tracer.withSpan(null))
				.map(msg -> this.traceMessageHandler.wrapInputMessage(msg,
						inputDestination(targetFunction.getFunctionDefinition())))
				.flatMap(msg -> Flux.deferContextual(ctx -> {
					MessageAndSpansAndScope messageAndSpansAndScope = ctx.get(MessageAndSpansAndScope.class);
					messageAndSpansAndScope.messageAndSpans = msg;
					messageAndSpansAndScope.span = msg.childSpan;
					setNameAndTag(targetFunction, msg.childSpan);
					messageAndSpansAndScope.scope = tracer.withSpan(msg.childSpan);
					return Mono.just(msg.msg);
				}));
		if (targetFunction.isConsumer()) {
			return targetFunction.apply(reactorStreamConsumer(flux));
		}
		final Flux<Message> function = ((Flux<Message>) targetFunction.apply(flux));
		return Flux.deferContextual(contextView -> {
			MessageAndSpansAndScope msg = contextView.get(MessageAndSpansAndScope.class);
			return function.doOnNext(message -> {
				msg.end();
				msg.handle();
			}).map(msgResult -> {
				MessageAndSpan messageAndSpan = traceMessageHandler.wrapOutputMessage(msgResult,
						msg.messageAndSpans.parentSpan, outputDestination(targetFunction.getFunctionDefinition()));
				traceMessageHandler.afterMessageHandled(messageAndSpan.span, null);
				return messageAndSpan.msg;
			})
					// TODO: Fix me when this is resolved in Reactor
					// .doOnSubscribe(__ -> scope.close())
					.doOnError(msg::error).doFinally(signalType -> {
						if (!msg.isHandled()) {
							msg.end();
						}
					});
		}).contextWrite(contextView -> contextView.put(MessageAndSpansAndScope.class, new MessageAndSpansAndScope()));
	}

	private Object reactorStreamConsumer(Object result) {
		if (result instanceof Mono) {
			return Mono.deferContextual(contextView -> {
				MessageAndSpansAndScope msg = contextView.get(MessageAndSpansAndScope.class);
				return ((Mono<Message>) result)
						// TODO: Fix me when this is resolved in Reactor
						// .doOnSubscribe(__ -> scope.close())
						.doOnError(msg::error).doFinally(signalType -> {
							msg.end();
						});
			}).contextWrite(
					contextView -> contextView.put(MessageAndSpansAndScope.class, new MessageAndSpansAndScope()));
		}
		return Flux.deferContextual(contextView -> {
			MessageAndSpansAndScope msg = contextView.get(MessageAndSpansAndScope.class);
			return ((Flux<Message>) result)
					// TODO: Fix me when this is resolved in Reactor
					// .doOnSubscribe(__ -> scope.close())
					.doOnError(msg::error).doFinally(signalType -> {
						msg.end();
					});
		}).contextWrite(contextView -> contextView.put(MessageAndSpansAndScope.class, new MessageAndSpansAndScope()));
	}

	private Object reactorStreamSupplier(Publisher<?> message,
			SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		Publisher<?> publisher = (Publisher<?>) targetFunction.get();
		if (publisher instanceof Mono) {
			if (log.isDebugEnabled()) {
				log.debug("Will instrument a stream Mono supplier");
			}
			Mono mono = (Mono) publisher;
			publisher = ReactorSleuth.tracedMono(tracer, tracer.currentTraceContext(),
					targetFunction.getFunctionDefinition(), () -> mono, (msg, s) -> {
						customizedInputMessageSpan(s, msg instanceof Message ? (Message) msg : null);
					}).map(object -> toMessage(object))
					.map(object -> this.getMessageAndSpans((Message) object, targetFunction.getFunctionDefinition(),
							setNameAndTag(targetFunction, tracer.currentSpan())))
					.doOnNext(wrappedOutputMessage -> customizedOutputMessageSpan(
							((MessageAndSpan) wrappedOutputMessage).span, ((MessageAndSpan) wrappedOutputMessage).msg))
					.doOnNext(wrappedOutputMessage -> traceMessageHandler
							.afterMessageHandled(((MessageAndSpan) wrappedOutputMessage).span, null))
					.map(wrappedOutputMessage -> ((MessageAndSpan) wrappedOutputMessage).msg);
		}
		else {
			if (log.isDebugEnabled()) {
				log.debug("Will instrument a stream Flux supplier");
			}
			Flux flux = (Flux) publisher;
			publisher = ReactorSleuth.tracedFlux(tracer, tracer.currentTraceContext(),
					targetFunction.getFunctionDefinition(), () -> flux, (msg, s) -> {
						customizedInputMessageSpan(s, msg instanceof Message ? (Message) msg : null);
					}).map(object -> toMessage(object))
					.map(object -> this.getMessageAndSpans((Message) object, targetFunction.getFunctionDefinition(),
							setNameAndTag(targetFunction, tracer.currentSpan())))
					.doOnNext(wrappedOutputMessage -> customizedOutputMessageSpan(
							((MessageAndSpan) wrappedOutputMessage).span, ((MessageAndSpan) wrappedOutputMessage).msg))
					.doOnNext(wrappedOutputMessage -> traceMessageHandler
							.afterMessageHandled(((MessageAndSpan) wrappedOutputMessage).span, null))
					.map(wrappedOutputMessage -> ((MessageAndSpan) wrappedOutputMessage).msg);
		}
		return publisher;
	}

	private Span setNameAndTag(SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction, Span span) {
		return span.name(targetFunction.getFunctionDefinition()).tag(SleuthMessagingSpan.Tags.FUNCTION_NAME.getKey(),
				targetFunction.getFunctionDefinition());
	}

	private Object nonReactorStream(Message<byte[]> message,
			SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		MessageAndSpans invocationMessage = null;
		Span span;
		if (message == null && targetFunction.isSupplier()) { // Supplier
			if (log.isDebugEnabled()) {
				log.debug("Creating a span for a supplier");
			}
			span = setNameAndTag(targetFunction, this.tracer.nextSpan());
			customizedInputMessageSpan(span, null);
		}
		else {
			if (log.isDebugEnabled()) {
				log.debug("Will retrieve the tracing headers from the message");
			}
			invocationMessage = this.traceMessageHandler.wrapInputMessage(message,
					inputDestination(targetFunction.getFunctionDefinition()));
			if (log.isDebugEnabled()) {
				log.debug("Wrapped input msg " + invocationMessage);
			}
			span = setNameAndTag(targetFunction, invocationMessage.childSpan);
		}
		Object result;
		Throwable throwable = null;
		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			result = invocationMessage == null ? targetFunction.get() : targetFunction.apply(invocationMessage.msg);
		}
		catch (Exception e) {
			throwable = e;
			throw e;
		}
		finally {
			this.traceMessageHandler.afterMessageHandled(span, throwable);
		}
		if (result == null) {
			if (log.isDebugEnabled()) {
				log.debug("Returned message is null - we have a consumer");
			}
			return null;
		}
		Message<?> msgResult = toMessage(result);
		MessageAndSpan wrappedOutputMessage;
		if (log.isDebugEnabled()) {
			log.debug("Will instrument the output message");
		}
		if (invocationMessage != null) {
			wrappedOutputMessage = this.traceMessageHandler.wrapOutputMessage(msgResult, invocationMessage.parentSpan,
					outputDestination(targetFunction.getFunctionDefinition()));
		}
		else {
			wrappedOutputMessage = this.getMessageAndSpans(msgResult, targetFunction.getFunctionDefinition(), span);
		}
		if (log.isDebugEnabled()) {
			log.debug("Wrapped output msg " + wrappedOutputMessage);
		}
		traceMessageHandler.afterMessageHandled(wrappedOutputMessage.span, null);
		return wrappedOutputMessage.msg;
	}

	MessageAndSpan getMessageAndSpans(Message<?> resultMessage, String name, Span spanFromMessage) {
		return traceMessageHandler.wrapOutputMessage(resultMessage, spanFromMessage, outputDestination(name));
	}

	private void customizedInputMessageSpan(Span spanToCustomize, Message<?> msg) {
		this.customizers.forEach(cust -> cust.customizeInputMessageSpan(spanToCustomize, msg));
	}

	private void customizedOutputMessageSpan(Span spanToCustomize, Message<?> msg) {
		this.customizers.forEach(cust -> cust.customizeOutputMessageSpan(spanToCustomize, msg));
	}

	private Message<?> toMessage(Object result) {
		if (!(result instanceof Message)) {
			return MessageBuilder.withPayload(result).build();
		}
		return (Message<?>) result;
	}

	String inputDestination(String functionDefinition) {
		return this.functionToDestinationCache.computeIfAbsent(functionDefinition, s -> {
			String bindingMappingProperty = "spring.cloud.stream.function.bindings." + s + "-in-0";
			String bindingProperty = this.environment.containsProperty(bindingMappingProperty)
					? this.environment.getProperty(bindingMappingProperty) : s + "-in-0";
			return this.environment.getProperty("spring.cloud.stream.bindings." + bindingProperty + ".destination", s);
		});
	}

	String outputDestination(String functionDefinition) {
		return this.functionToDestinationCache.computeIfAbsent(functionDefinition, s -> {
			String bindingMappingProperty = "spring.cloud.stream.function.bindings." + s + "-out-0";
			String bindingProperty = this.environment.containsProperty(bindingMappingProperty)
					? this.environment.getProperty(bindingMappingProperty) : s + "-out-0";
			return this.environment.getProperty("spring.cloud.stream.bindings." + bindingProperty + ".destination", s);
		});
	}

	@Override
	public void onApplicationEvent(RefreshScopeRefreshedEvent event) {
		if (log.isDebugEnabled()) {
			log.debug("Context refreshed, will reset the cache");
		}
		this.functionToDestinationCache.clear();
	}

	static class MessageAndSpansAndScope {

		MessageAndSpans messageAndSpans;

		Span span;

		Tracer.SpanInScope scope;

		boolean handled;

		void error(Throwable throwable) {
			if (this.span != null) {
				this.span.error(throwable);
			}
		}

		void handle() {
			this.handled = true;
		}

		boolean isHandled() {
			return this.handled;
		}

		void end() {
			if (this.span != null) {
				this.span.end();
			}
			if (this.scope != null) {
				this.scope.close();
			}
		}

	}

}
