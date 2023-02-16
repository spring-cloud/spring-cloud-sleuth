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
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.frame.FrameType;
import io.rsocket.metadata.CompositeMetadataCodec;
import io.rsocket.metadata.RoutingMetadata;
import io.rsocket.metadata.TracingMetadataCodec;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.RSocketProxy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpanBuilder;
import org.springframework.cloud.sleuth.internal.EncodingUtils;
import org.springframework.cloud.sleuth.propagation.Propagator;

/**
 * Tracing representation of a {@link RSocketProxy} for the requester.
 *
 * @author Marcin Grzejszczak
 * @author Oleh Dokuka
 * @since 3.1.0
 */
public class TracingRequesterRSocketProxy extends RSocketProxy {

	private static final Log log = LogFactory.getLog(TracingRequesterRSocketProxy.class);

	private final Propagator propagator;

	private final Propagator.Setter<CompositeByteBuf> setter;

	private final Tracer tracer;

	private final boolean isZipkinPropagationEnabled;

	public TracingRequesterRSocketProxy(RSocket source, Propagator propagator,
			Propagator.Setter<CompositeByteBuf> setter, Tracer tracer, boolean isZipkinPropagationEnabled) {
		super(source);
		this.propagator = propagator;
		this.setter = setter;
		this.tracer = tracer;
		this.isZipkinPropagationEnabled = isZipkinPropagationEnabled;
	}

	private void clearThreadLocal() {
		this.tracer.withSpan(null);
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		clearThreadLocal();
		return setSpan(super::fireAndForget, payload, FrameType.REQUEST_FNF);
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		clearThreadLocal();
		return setSpan(super::requestResponse, payload, FrameType.REQUEST_RESPONSE);
	}

	<T> Mono<T> setSpan(Function<Payload, Mono<T>> input, Payload payload, FrameType frameType) {
		return Mono.deferContextual(contextView -> {
			Span.Builder spanBuilder = spanBuilder(contextView);
			ByteBuf extracted = CompositeMetadataUtils.extract(payload.sliceMetadata(),
					WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString());
			// TODO: do sth about extracted == null, log that tracing can't be used or sth
			final RoutingMetadata routingMetadata = new RoutingMetadata(extracted);
			final Iterator<String> iterator = routingMetadata.iterator();
			String route = iterator.next();
			Span span = AssertingSpanBuilder
					.of(SleuthRSocketSpan.RSOCKET_REQUESTER_SPAN, spanBuilder.kind(Span.Kind.PRODUCER))
					.name(frameType.name() + " " + route).tag(SleuthRSocketSpan.Tags.ROUTE, route)
					.tag(SleuthRSocketSpan.Tags.REQUEST_TYPE, frameType.name()).start();
			if (log.isDebugEnabled()) {
				log.debug("Extracted result from context or thread local " + span);
			}
			final Payload newPayload = PayloadUtils.cleanTracingMetadata(payload, new HashSet<>(propagator.fields()));
			final TraceContext traceContext = span.context();
			final CompositeByteBuf metadata = (CompositeByteBuf) newPayload.metadata();
			if (this.isZipkinPropagationEnabled) {
				injectDefaultZipkinRSocketHeaders(metadata, traceContext);
			}
			this.propagator.inject(traceContext, metadata, this.setter);
			return input.apply(newPayload).doOnError(span::error).doFinally(signalType -> span.end());
		});
	}

	void injectDefaultZipkinRSocketHeaders(CompositeByteBuf metadata, TraceContext traceContext) {
		TracingMetadataCodec.Flags flags = traceContext.sampled() == null ? TracingMetadataCodec.Flags.UNDECIDED
				: traceContext.sampled() ? TracingMetadataCodec.Flags.SAMPLE : TracingMetadataCodec.Flags.NOT_SAMPLE;
		String traceId = traceContext.traceId();
		long[] traceIds = EncodingUtils.fromString(traceId);
		long[] spanId = EncodingUtils.fromString(traceContext.spanId());
		long[] parentSpanId = EncodingUtils.fromString(traceContext.parentId());
		boolean isTraceId128Bit = traceIds.length == 2;

		final ByteBufAllocator allocator = metadata.alloc();
		if (isTraceId128Bit) {
			CompositeMetadataCodec.encodeAndAddMetadata(metadata, allocator,
					WellKnownMimeType.MESSAGE_RSOCKET_TRACING_ZIPKIN,
					TracingMetadataCodec.encode128(allocator, traceIds[0], traceIds[1], spanId[0],
							EncodingUtils.fromString(traceContext.parentId())[0], flags));
		}
		else {
			CompositeMetadataCodec.encodeAndAddMetadata(metadata, allocator,
					WellKnownMimeType.MESSAGE_RSOCKET_TRACING_ZIPKIN,
					TracingMetadataCodec.encode64(allocator, traceIds[0], spanId[0], parentSpanId[0], flags));
		}
	}

	Span.Builder spanBuilder(ContextView contextView) {
		Span.Builder spanBuilder = this.tracer.spanBuilder();
		if (contextView.hasKey(TraceContext.class)) {
			spanBuilder = spanBuilder.setParent(contextView.get(TraceContext.class));
		}
		else if (this.tracer.currentSpan() != null) {
			spanBuilder = spanBuilder.setParent(this.tracer.currentSpan().context());
		}
		return spanBuilder;
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		clearThreadLocal();
		return Flux.deferContextual(contextView -> setSpan(super::requestStream, payload, contextView));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> inbound) {
		clearThreadLocal();
		return Flux.from(inbound).switchOnFirst((firstSignal, flux) -> {
			final Payload firstPayload = firstSignal.get();
			if (firstPayload != null) {
				return setSpan(p -> super.requestChannel(flux.skip(1).startWith(p)), firstPayload,
						firstSignal.getContextView());
			}
			return flux;
		});
	}

	<T> Flux<Payload> setSpan(Function<Payload, Flux<Payload>> input, Payload payload, ContextView contextView) {
		Span.Builder spanBuilder = spanBuilder(contextView);
		final RoutingMetadata routingMetadata = new RoutingMetadata(CompositeMetadataUtils
				.extract(payload.sliceMetadata(), WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString()));
		final Iterator<String> iterator = routingMetadata.iterator();
		Span span = AssertingSpanBuilder
				.of(SleuthRSocketSpan.RSOCKET_REQUESTER_SPAN, spanBuilder.kind(Span.Kind.PRODUCER))
				.name(iterator.next()).start();
		if (log.isDebugEnabled()) {
			log.debug("Extracted result from context or thread local " + span);
		}
		final Payload newPayload = PayloadUtils.cleanTracingMetadata(payload, new HashSet<>(propagator.fields()));
		this.propagator.inject(span.context(), (CompositeByteBuf) newPayload.metadata(), this.setter);
		return input.apply(newPayload).doOnError(span::error).doFinally(signalType -> span.end());
	}

}
