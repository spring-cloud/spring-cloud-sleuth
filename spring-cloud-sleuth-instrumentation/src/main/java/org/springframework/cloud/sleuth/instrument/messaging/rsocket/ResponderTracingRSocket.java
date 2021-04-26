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

package org.springframework.cloud.sleuth.instrument.messaging.rsocket;

import static org.springframework.cloud.sleuth.instrument.messaging.rsocket.PayloadUtils.cleanTracingMetadata;

import io.netty.buffer.ByteBuf;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.frame.FrameType;
import io.rsocket.metadata.RoutingMetadata;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.RSocketProxy;
import java.util.HashSet;
import java.util.Iterator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.propagation.Propagator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ResponderTracingRSocket extends RSocketProxy {

	private static final Log log = LogFactory.getLog(ResponderTracingRSocket.class);

	final Propagator propagator;

	final Propagator.Getter<ByteBuf> getter;

	// final MessageSpanCustomizer messageSpanCustomizer;
	final Tracer tracer;

	private final ThreadLocalSpan threadLocalSpan = new ThreadLocalSpan();

	//
	// public ResponderTracingRSocket(RSocket source, Propagator propagator,
	// Propagator.Setter<ByteBuf> setter, Propagator.Getter<ByteBuf> getter,
	// MessageSpanCustomizer messageSpanCustomizer, Tracer tracer) {

	public ResponderTracingRSocket(RSocket source, Propagator propagator,
			Propagator.Getter<ByteBuf> getter,
			Tracer tracer) {
		super(source);
		this.propagator = propagator;
		this.getter = getter;
		// this.messageSpanCustomizer = messageSpanCustomizer;
		this.tracer = tracer;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		// called on Netty EventLoop
		// there can't be trace context in thread local here
		// payload -> metadata -> X-B3-TraceId = a , X-B3-SpanId = b
		Span consumerSpan = consumerSpan(payload, payload.sliceMetadata(), FrameType.REQUEST_FNF);
		// create and scope a span for the message processor
		Span handle = this.tracer.nextSpan(consumerSpan).start();
		// TODO: Convert Payload to Spring Message?
		// handle = this.messageSpanCustomizer.customizeHandle(handle, message,
		// channel).start();
		if (log.isDebugEnabled()) {
			log.debug("Created consumer span " + handle);
		}
		setSpanInScope(handle);

		// incomingSpan's PARENT - a, b
		// MessageHeaderPropagatorSetter.removeAnyTraceHeaders(headers,
		// this.propagator.fields());

		final Payload newPayload = cleanTracingMetadata(payload,
				new HashSet<>(propagator.fields()));

		return super.fireAndForget(newPayload)
				// TODO: Put TraceContext and Span into reactor context
				.contextWrite(context -> context.put(Span.class, handle)
						.put(TraceContext.class, handle.context()))
				.doOnError(this::finishSpan).doOnSuccess(__ -> finishSpan(null))
				.doOnCancel(() -> finishSpan(null));
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		// called on Netty EventLoop
		// there can't be trace context in thread local here
		// payload -> metadata -> X-B3-TraceId = a , X-B3-SpanId = b
		Span consumerSpan = consumerSpan(payload, payload.sliceMetadata(),
				FrameType.REQUEST_RESPONSE);
		// create and scope a span for the message processor
		Span handle = this.tracer.nextSpan(consumerSpan).start();
		// TODO: Convert Payload to Spring Message?
		// handle = this.messageSpanCustomizer.customizeHandle(handle, message,
		// channel).start();
		if (log.isDebugEnabled()) {
			log.debug("Created consumer span " + handle);
		}
		setSpanInScope(handle);

		// incomingSpan's PARENT - a, b
		// MessageHeaderPropagatorSetter.removeAnyTraceHeaders(headers,
		// this.propagator.fields());

		final Payload newPayload = cleanTracingMetadata(payload,
				new HashSet<>(propagator.fields()));

		return super.requestResponse(newPayload)
				// TODO: Put TraceContext and Span into reactor context
				.contextWrite(context -> context.put(Span.class, handle)
						.put(TraceContext.class, handle.context()))
				.doOnError(this::finishSpan).doOnSuccess(__ -> finishSpan(null))
				.doOnCancel(() -> finishSpan(null));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		// called on Netty EventLoop
		// there can't be trace context in thread local here
		// payload -> metadata -> X-B3-TraceId = a , X-B3-SpanId = b
		Span consumerSpan = consumerSpan(payload, payload.sliceMetadata(),
				FrameType.REQUEST_STREAM);
		// create and scope a span for the message processor
		Span handle = this.tracer.nextSpan(consumerSpan).start();
		// TODO: Convert Payload to Spring Message?
		// handle = this.messageSpanCustomizer.customizeHandle(handle, message,
		// channel).start();
		if (log.isDebugEnabled()) {
			log.debug("Created consumer span " + handle);
		}
		setSpanInScope(handle);

		// incomingSpan's PARENT - a, b
		// MessageHeaderPropagatorSetter.removeAnyTraceHeaders(headers,
		// this.propagator.fields());

		final Payload newPayload = cleanTracingMetadata(payload,
				new HashSet<>(propagator.fields()));

		return super.requestStream(newPayload)
				// TODO: Put TraceContext and Span into reactor context
				.contextWrite(context -> context.put(Span.class, handle)
						.put(TraceContext.class, handle.context()))
				.doOnError(this::finishSpan).doOnComplete(() -> finishSpan(null))
				.doOnCancel(() -> finishSpan(null));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return Flux.from(payloads).switchOnFirst((firstSignal, flux) -> {
			final Payload firstPayload = firstSignal.get();
			if (firstPayload != null) {
				// called on Netty EventLoop
				// there can't be trace context in thread local here
				// payload -> metadata -> X-B3-TraceId = a , X-B3-SpanId = b
				Span consumerSpan = consumerSpan(firstPayload, firstPayload.sliceMetadata(),
						FrameType.REQUEST_CHANNEL);
				// create and scope a span for the message processor
				Span handle = this.tracer.nextSpan(consumerSpan).start();
				// TODO: Convert Payload to Spring Message?
				// handle = this.messageSpanCustomizer.customizeHandle(handle, message,
				// channel).start();
				if (log.isDebugEnabled()) {
					log.debug("Created consumer span " + handle);
				}
				setSpanInScope(handle);

				// incomingSpan's PARENT - a, b
				// MessageHeaderPropagatorSetter.removeAnyTraceHeaders(headers,
				// this.propagator.fields());

				final Payload newPayload = cleanTracingMetadata(firstPayload,
						new HashSet<>(propagator.fields()));

				return super.requestChannel(flux.skip(1).startWith(newPayload))
						// TODO: Put TraceContext and Span into reactor context
						.contextWrite(
								context -> context.put(Span.class, handle)
										.put(TraceContext.class, handle.context()))
						.doOnError(this::finishSpan).doOnComplete(() -> finishSpan(null))
						.doOnCancel(() -> finishSpan(null));
			}

			return flux;
		});
	}

	// TODO: Copy from SI
	private Span consumerSpan(Payload payload, ByteBuf headers, FrameType requestType) {
		Span.Builder consumerSpanBuilder = this.propagator.extract(headers, this.getter);
		if (log.isDebugEnabled()) {
			log.debug("Extracted result from headers - will finish it immediately "
					+ consumerSpanBuilder);
		}
		final RoutingMetadata routingMetadata = new RoutingMetadata(
				CompositeMetadataUtils
						.extract(headers, WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString()));
		final Iterator<String> iterator = routingMetadata.iterator();

		// Start and finish a consumer span as we will immediately process it.
		consumerSpanBuilder.kind(Span.Kind.CONSUMER)
				.name(requestType.name() + " " + iterator.next()).start();

		// TODO: What to do about it? In SI we know that this would be the broker
		// TODO: if in the headers broker has added a header we will set this to broker
		// consumerSpanBuilder.remoteServiceName(REMOTE_SERVICE_NAME);
		// TODO: Convert payload to message?
		// consumerSpanBuilder =
		// this.messageSpanCustomizer.customizeHandle(consumerSpanBuilder, payload, null);
		Span consumerSpan = consumerSpanBuilder.start();
		consumerSpan.end();
		return consumerSpan;
	}

	// TODO: Copy from SI
	private void setSpanInScope(Span span) {
		Tracer.SpanInScope spanInScope = this.tracer.withSpan(span);
		this.threadLocalSpan.set(new SpanAndScope(span, spanInScope));
		if (log.isDebugEnabled()) {
			log.debug("Put span in scope " + span);
		}
	}

	// TODO: Copy from SI
	void finishSpan(Throwable error) {
		SpanAndScope spanAndScope = getSpanFromThreadLocal();
		if (spanAndScope == null) {
			return;
		}
		Span span = spanAndScope.span;
		Tracer.SpanInScope scope = spanAndScope.scope;
		if (span.isNoop()) {
			if (log.isDebugEnabled()) {
				log.debug("Span " + span + " is noop - will stope the scope");
			}
			scope.close();
			return;
		}
		if (error != null) { // an error occurred, adding error to span
			span.error(error);
		}
		if (log.isDebugEnabled()) {
			log.debug("Will finish the and its corresponding scope " + span);
		}
		span.end();
		scope.close();
	}

	// TODO: Copy from SI
	private SpanAndScope getSpanFromThreadLocal() {
		SpanAndScope span = this.threadLocalSpan.get();
		if (log.isDebugEnabled()) {
			log.debug("Took span [" + span + "] from thread local");
		}
		this.threadLocalSpan.remove();
		return span;
	}

}
