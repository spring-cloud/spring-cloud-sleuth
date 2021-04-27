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

import io.netty.buffer.CompositeByteBuf;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.metadata.RoutingMetadata;
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

	public TracingRequesterRSocketProxy(RSocket source, Propagator propagator,
			Propagator.Setter<CompositeByteBuf> setter, Tracer tracer) {
		super(source);
		this.propagator = propagator;
		this.setter = setter;
		this.tracer = tracer;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return setSpan(super::fireAndForget, payload);
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return setSpan(super::requestResponse, payload);
	}

	<T> Mono<T> setSpan(Function<Payload, Mono<T>> input, Payload payload) {
		return Mono.deferContextual(contextView -> {
			Span.Builder spanBuilder = spanBuilder(contextView);
			final RoutingMetadata routingMetadata = new RoutingMetadata(CompositeMetadataUtils
					.extract(payload.sliceMetadata(), WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString()));
			final Iterator<String> iterator = routingMetadata.iterator();
			Span span = spanBuilder.kind(Span.Kind.PRODUCER).name(iterator.next()).start();
			if (log.isDebugEnabled()) {
				log.debug("Extracted result from context or thread local " + span);
			}
			final Payload newPayload = PayloadUtils.cleanTracingMetadata(payload, new HashSet<>(propagator.fields()));
			this.propagator.inject(span.context(), (CompositeByteBuf) newPayload.metadata(), this.setter);
			return input.apply(newPayload).doOnError(span::error).doFinally(signalType -> span.end());
		});
	}

	private Span.Builder spanBuilder(ContextView contextView) {
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
		return Flux.deferContextual(contextView -> setSpan(super::requestStream, payload, contextView));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> inbound) {
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
		Span span = spanBuilder.kind(Span.Kind.PRODUCER).name(iterator.next()).start();
		if (log.isDebugEnabled()) {
			log.debug("Extracted result from context or thread local " + span);
		}
		final Payload newPayload = PayloadUtils.cleanTracingMetadata(payload, new HashSet<>(propagator.fields()));
		this.propagator.inject(span.context(), (CompositeByteBuf) newPayload.metadata(), this.setter);
		return input.apply(newPayload).doOnError(span::error).doFinally(signalType -> span.end());
	}

}
