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

package org.springframework.cloud.sleuth.instrument.rsocket;

import java.util.HashSet;
import java.util.Iterator;

import io.netty.buffer.ByteBuf;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.frame.FrameType;
import io.rsocket.metadata.RoutingMetadata;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.RSocketProxy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.ThreadLocalSpan;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.WithThreadLocalSpan;
import org.springframework.cloud.sleuth.propagation.Propagator;

/**
 * Tracing representation of a {@link RSocketProxy} for the responder.
 *
 * @author Marcin Grzejszczak
 * @author Oleh Dokuka
 * @since 3.1.0
 */
public class TracingResponderRSocketProxy extends RSocketProxy implements WithThreadLocalSpan {

	private static final Log log = LogFactory.getLog(TracingResponderRSocketProxy.class);

	private final Propagator propagator;

	private final Propagator.Getter<ByteBuf> getter;

	private final Tracer tracer;

	private final ThreadLocalSpan threadLocalSpan;

	public TracingResponderRSocketProxy(RSocket source, Propagator propagator, Propagator.Getter<ByteBuf> getter,
			Tracer tracer) {
		super(source);
		this.propagator = propagator;
		this.getter = getter;
		// this.messageSpanCustomizer = messageSpanCustomizer;
		this.tracer = tracer;
		this.threadLocalSpan = new ThreadLocalSpan(tracer);
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		// called on Netty EventLoop
		// there can't be trace context in thread local here
		Span consumerSpan = consumerSpan(payload, payload.sliceMetadata(), FrameType.REQUEST_FNF);
		// create and scope a span for the message processor
		Span handle = this.tracer.nextSpan(consumerSpan).start();
		if (log.isDebugEnabled()) {
			log.debug("Created consumer span " + handle);
		}
		setSpanInScope(handle);
		final Payload newPayload = PayloadUtils.cleanTracingMetadata(payload, new HashSet<>(propagator.fields()));
		return super.fireAndForget(newPayload)
				.contextWrite(context -> context.put(Span.class, handle).put(TraceContext.class, handle.context()))
				.doOnError(this::finishSpan).doOnSuccess(__ -> finishSpan(null)).doOnCancel(() -> finishSpan(null));
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		Span consumerSpan = consumerSpan(payload, payload.sliceMetadata(), FrameType.REQUEST_RESPONSE);
		Span handle = this.tracer.nextSpan(consumerSpan).start();
		if (log.isDebugEnabled()) {
			log.debug("Created consumer span " + handle);
		}
		setSpanInScope(handle);
		final Payload newPayload = PayloadUtils.cleanTracingMetadata(payload, new HashSet<>(propagator.fields()));
		return super.requestResponse(newPayload)
				.contextWrite(context -> context.put(Span.class, handle).put(TraceContext.class, handle.context()))
				.doOnError(this::finishSpan).doOnSuccess(__ -> finishSpan(null)).doOnCancel(() -> finishSpan(null));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		Span consumerSpan = consumerSpan(payload, payload.sliceMetadata(), FrameType.REQUEST_STREAM);
		Span handle = this.tracer.nextSpan(consumerSpan).start();
		if (log.isDebugEnabled()) {
			log.debug("Created consumer span " + handle);
		}
		setSpanInScope(handle);
		final Payload newPayload = PayloadUtils.cleanTracingMetadata(payload, new HashSet<>(propagator.fields()));
		return super.requestStream(newPayload)
				.contextWrite(context -> context.put(Span.class, handle).put(TraceContext.class, handle.context()))
				.doOnError(this::finishSpan).doOnComplete(() -> finishSpan(null)).doOnCancel(() -> finishSpan(null));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return Flux.from(payloads).switchOnFirst((firstSignal, flux) -> {
			final Payload firstPayload = firstSignal.get();
			if (firstPayload != null) {
				Span consumerSpan = consumerSpan(firstPayload, firstPayload.sliceMetadata(), FrameType.REQUEST_CHANNEL);
				Span handle = this.tracer.nextSpan(consumerSpan).start();
				if (log.isDebugEnabled()) {
					log.debug("Created consumer span " + handle);
				}
				setSpanInScope(handle);
				final Payload newPayload = PayloadUtils.cleanTracingMetadata(firstPayload,
						new HashSet<>(propagator.fields()));
				return super.requestChannel(flux.skip(1).startWith(newPayload))
						.contextWrite(
								context -> context.put(Span.class, handle).put(TraceContext.class, handle.context()))
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
			log.debug("Extracted result from headers - will finish it immediately " + consumerSpanBuilder);
		}
		final RoutingMetadata routingMetadata = new RoutingMetadata(
				CompositeMetadataUtils.extract(headers, WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString()));
		final Iterator<String> iterator = routingMetadata.iterator();

		// Start and finish a consumer span as we will immediately process it.
		consumerSpanBuilder.kind(Span.Kind.CONSUMER).name(requestType.name() + " " + iterator.next()).start();

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

	@Override
	public ThreadLocalSpan getThreadLocalSpan() {
		return this.threadLocalSpan;
	}

}
