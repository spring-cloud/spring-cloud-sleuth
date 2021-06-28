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
import io.rsocket.metadata.TracingMetadata;
import io.rsocket.metadata.TracingMetadataCodec;
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
import org.springframework.cloud.sleuth.docs.AssertingSpanBuilder;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.cloud.sleuth.internal.EncodingUtils;
import org.springframework.cloud.sleuth.propagation.Propagator;

/**
 * Tracing representation of a {@link RSocketProxy} for the responder.
 *
 * @author Marcin Grzejszczak
 * @author Oleh Dokuka
 * @since 3.1.0
 */
public class TracingResponderRSocketProxy extends RSocketProxy {

	private static final Log log = LogFactory.getLog(TracingResponderRSocketProxy.class);

	private final Propagator propagator;

	private final Propagator.Getter<ByteBuf> getter;

	private final Tracer tracer;

	private final ThreadLocalSpan threadLocalSpan;

	private final boolean isZipkinPropagationEnabled;

	public TracingResponderRSocketProxy(RSocket source, Propagator propagator, Propagator.Getter<ByteBuf> getter,
			Tracer tracer, boolean isZipkinPropagationEnabled) {
		super(source);
		this.propagator = propagator;
		this.getter = getter;
		this.tracer = tracer;
		this.threadLocalSpan = new ThreadLocalSpan(tracer);
		this.isZipkinPropagationEnabled = isZipkinPropagationEnabled;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		// called on Netty EventLoop
		// there can't be trace context in thread local here
		Span handle = consumerSpanBuilder(payload.sliceMetadata(), FrameType.REQUEST_FNF);
		if (log.isDebugEnabled()) {
			log.debug("Created consumer span " + handle);
		}
		final Payload newPayload = PayloadUtils.cleanTracingMetadata(payload, new HashSet<>(propagator.fields()));
		return ReactorSleuth.tracedMono(this.tracer, handle, () -> super.fireAndForget(newPayload));
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		Span handle = consumerSpanBuilder(payload.sliceMetadata(), FrameType.REQUEST_RESPONSE);
		if (log.isDebugEnabled()) {
			log.debug("Created consumer span " + handle);
		}
		final Payload newPayload = PayloadUtils.cleanTracingMetadata(payload, new HashSet<>(propagator.fields()));
		return ReactorSleuth.tracedMono(this.tracer, handle, () -> super.requestResponse(newPayload));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		Span handle = consumerSpanBuilder(payload.sliceMetadata(), FrameType.REQUEST_STREAM);
		if (log.isDebugEnabled()) {
			log.debug("Created consumer span " + handle);
		}
		final Payload newPayload = PayloadUtils.cleanTracingMetadata(payload, new HashSet<>(propagator.fields()));
		return ReactorSleuth.tracedFlux(this.tracer, handle, () -> super.requestStream(newPayload));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return Flux.from(payloads).switchOnFirst((firstSignal, flux) -> {
			final Payload firstPayload = firstSignal.get();
			if (firstPayload != null) {
				Span handle = consumerSpanBuilder(firstPayload.sliceMetadata(), FrameType.REQUEST_CHANNEL);
				if (handle == null) {
					return super.requestChannel(flux);
				}
				if (log.isDebugEnabled()) {
					log.debug("Created consumer span " + handle);
				}
				final Payload newPayload = PayloadUtils.cleanTracingMetadata(firstPayload,
						new HashSet<>(propagator.fields()));
				return ReactorSleuth.tracedFlux(this.tracer, handle,
						() -> super.requestChannel(flux.skip(1).startWith(newPayload)));
			}
			return flux;
		});
	}

	private Span consumerSpanBuilder(ByteBuf headers, FrameType requestType) {
		Span.Builder consumerSpanBuilder = consumerSpanBuilder(headers);
		if (log.isDebugEnabled()) {
			log.debug("Extracted result from headers " + consumerSpanBuilder);
		}
		final ByteBuf extract = CompositeMetadataUtils.extract(headers,
				WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString());
		String name = "handle";
		if (extract != null) {
			final RoutingMetadata routingMetadata = new RoutingMetadata(extract);
			final Iterator<String> iterator = routingMetadata.iterator();
			name = requestType.name() + " " + iterator.next();
		}
		return AssertingSpanBuilder
				.of(SleuthRSocketSpan.RSOCKET_RESPONDER_SPAN, consumerSpanBuilder.kind(Span.Kind.CONSUMER)).name(name)
				.start();
	}

	private Span.Builder consumerSpanBuilder(ByteBuf headers) {
		if (this.isZipkinPropagationEnabled) {
			ByteBuf extract = CompositeMetadataUtils.extract(headers,
					WellKnownMimeType.MESSAGE_RSOCKET_TRACING_ZIPKIN.getString());
			if (extract != null) {
				TracingMetadata tracingMetadata = TracingMetadataCodec.decode(extract);
				Span.Builder builder = this.tracer.spanBuilder();
				String traceId = EncodingUtils.fromLong(tracingMetadata.traceId());
				long traceIdHigh = tracingMetadata.traceIdHigh();
				if (traceIdHigh != 0L) {
					// ExtendedTraceId
					traceId = EncodingUtils.fromLong(traceIdHigh) + traceId;
				}
				TraceContext.Builder parentBuilder = this.tracer.traceContextBuilder()
						.sampled(tracingMetadata.isDebug() || tracingMetadata.isSampled())
						.traceId(traceId)
						.spanId(EncodingUtils.fromLong(tracingMetadata.spanId()))
						.parentId(EncodingUtils.fromLong(tracingMetadata.parentId()));
				return builder.setParent(parentBuilder.build());
			}
			else {
				return this.propagator.extract(headers, this.getter);
			}
		}
		return this.propagator.extract(headers, this.getter);
	}

}
