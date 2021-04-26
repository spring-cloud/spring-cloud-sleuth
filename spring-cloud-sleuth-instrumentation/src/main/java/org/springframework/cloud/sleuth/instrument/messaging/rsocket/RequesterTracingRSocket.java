package org.springframework.cloud.sleuth.instrument.messaging.rsocket;

import static org.springframework.cloud.sleuth.instrument.messaging.rsocket.PayloadUtils.cleanTracingMetadata;

import io.netty.buffer.CompositeByteBuf;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.metadata.RoutingMetadata;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.RSocketProxy;
import java.util.HashSet;
import java.util.Iterator;
import java.util.function.Function;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.propagation.Propagator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

public class RequesterTracingRSocket extends RSocketProxy {

	private static final Log log = LogFactory.getLog(RequesterTracingRSocket.class);

	final Propagator propagator;

	final Propagator.Setter<CompositeByteBuf> setter;

	// final MessageSpanCustomizer messageSpanCustomizer;
	final Tracer tracer;

	//
	// public ResponderTracingRSocket(RSocket source, Propagator propagator,
	// Propagator.Setter<ByteBuf> setter, Propagator.Getter<ByteBuf> getter,
	// MessageSpanCustomizer messageSpanCustomizer, Tracer tracer) {

	public RequesterTracingRSocket(RSocket source, Propagator propagator, Propagator.Setter<CompositeByteBuf> setter,
			Tracer tracer) {
		super(source);
		this.propagator = propagator;
		this.setter = setter;
		// this.messageSpanCustomizer = messageSpanCustomizer;
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
			Span.Builder spanBuilder = this.tracer.spanBuilder();
			// TODO: customizing
			// spanBuilder = this.messageSpanCustomizer.customizeSend(spanBuilder,
			// message, channel);
			if (contextView.hasKey(TraceContext.class)) {
				spanBuilder = spanBuilder.setParent(contextView.get(TraceContext.class));
			} else if (this.tracer.currentSpan() != null) {
				// a use case where e.g. rSocketRequest is used outside spring
				spanBuilder = spanBuilder.setParent(this.tracer.currentSpan().context());
			}

			final RoutingMetadata routingMetadata = new RoutingMetadata(CompositeMetadataUtils
					.extract(payload.sliceMetadata(),
							WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString()));
			final Iterator<String> iterator = routingMetadata.iterator();

			Span span = spanBuilder.kind(Span.Kind.PRODUCER).name(iterator.next()).start();
			if (log.isDebugEnabled()) {
				log.debug("Extracted result from context or thread local " + span);
			}
			final Payload newPayload = cleanTracingMetadata(payload,
					new HashSet<>(propagator.fields()));
			this.propagator
					.inject(span.context(), (CompositeByteBuf) newPayload.metadata(), this.setter);

			return input.apply(newPayload).doOnError(span::error)
					.doFinally(signalType -> span.end());
		});
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
				return setSpan(p -> super.requestChannel(flux.skip(1).startWith(p)), firstPayload, firstSignal.getContextView());
			}

			return flux;
		});
	}

	<T> Flux<Payload> setSpan(Function<Payload, Flux<Payload>> input, Payload payload, ContextView contextView) {
		Span.Builder spanBuilder = this.tracer.spanBuilder();
		// TODO: customizing
		// spanBuilder = this.messageSpanCustomizer.customizeSend(spanBuilder,
		// message, channel);
		if (contextView.hasKey(TraceContext.class)) {
			spanBuilder = spanBuilder.setParent(contextView.get(TraceContext.class));
		} else if (this.tracer.currentSpan() != null) {
			// a use case where e.g. rSocketRequest is used outside spring
			spanBuilder = spanBuilder.setParent(this.tracer.currentSpan().context());
		}

		final RoutingMetadata routingMetadata = new RoutingMetadata(CompositeMetadataUtils
				.extract(payload.sliceMetadata(),
						WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString()));
		final Iterator<String> iterator = routingMetadata.iterator();

		Span span = spanBuilder.kind(Span.Kind.PRODUCER).name(iterator.next()).start();
		if (log.isDebugEnabled()) {
			log.debug("Extracted result from context or thread local " + span);
		}
		final Payload newPayload = cleanTracingMetadata(payload,
				new HashSet<>(propagator.fields()));
		this.propagator
				.inject(span.context(), (CompositeByteBuf) newPayload.metadata(), this.setter);

		return input.apply(newPayload).doOnError(span::error)
				.doFinally(signalType -> span.end());
	}

}
