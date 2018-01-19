package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.function.Function;
import java.util.function.Predicate;

import brave.Tracing;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.core.publisher.Operators;
import org.reactivestreams.Publisher;

/**
 * Reactive Span pointcuts factories
 *
 * @author Stephane Maldini
 * @since 2.0.0
 */
public abstract class ReactorSleuth {

	/**
	 * Return a span operator pointcut given a {@link Tracing}. This can be used in reactor
	 * via {@link reactor.core.publisher.Flux#transform(Function)}, {@link
	 * reactor.core.publisher.Mono#transform(Function)}, {@link
	 * reactor.core.publisher.Hooks#onEachOperator(Function)} or {@link
	 * reactor.core.publisher.Hooks#onLastOperator(Function)}.
	 *
	 * @param tracing the {@link Tracing} instance to use in this span operator
	 * @param <T> an arbitrary type that is left unchanged by the span operator
	 *
	 * @return a new Span operator pointcut
	 */
	public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> spanOperator(
			Tracing tracing) {
		return Operators.lift(POINTCUT_FILTER, ((scannable, sub) -> {
			//do not trace fused flows
			if(scannable instanceof Fuseable && sub instanceof Fuseable.QueueSubscription){
				return sub;
			}
			return new SpanSubscriber<>(
					sub,
					sub.currentContext(),
					tracing,
					scannable.name());
		}));
	}

	private static final Predicate<Scannable> POINTCUT_FILTER =
			s ->  !(s instanceof Fuseable.ScalarCallable);

	private ReactorSleuth() {
	}
}
