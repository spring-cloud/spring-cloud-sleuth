/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.circuitbreaker;

import java.util.function.Function;
import java.util.function.Supplier;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;

class TraceReactiveCircuitBreaker implements ReactiveCircuitBreaker {

	private final ReactiveCircuitBreaker delegate;

	private final Tracer tracer;

	private final CurrentTraceContext currentTraceContext;

	TraceReactiveCircuitBreaker(ReactiveCircuitBreaker delegate, Tracer tracer,
			CurrentTraceContext currentTraceContext) {
		this.delegate = delegate;
		this.tracer = tracer;
		this.currentTraceContext = currentTraceContext;
	}

	@Override
	public <T> Mono<T> run(Mono<T> toRun) {
		return runAndTraceMono(() -> this.delegate.run(toRun));
	}

	@Override
	public <T> Mono<T> run(Mono<T> toRun, Function<Throwable, Mono<T>> fallback) {
		return runAndTraceMono(
				() -> this.delegate.run(toRun, fallback != null ? new TraceFunction<>(this.tracer, fallback) : null));
	}

	@Override
	public <T> Flux<T> run(Flux<T> toRun) {
		return runAndTraceFlux(() -> this.delegate.run(toRun));
	}

	@Override
	public <T> Flux<T> run(Flux<T> toRun, Function<Throwable, Flux<T>> fallback) {
		return runAndTraceFlux(
				() -> this.delegate.run(toRun, fallback != null ? new TraceFunction<>(this.tracer, fallback) : null));
	}

	private <T> Mono<T> runAndTraceMono(Supplier<Mono<T>> mono) {
		return ReactorSleuth.tracedMono(this.tracer, this.currentTraceContext, "function", mono);
	}

	private <T> Flux<T> runAndTraceFlux(Supplier<Flux<T>> flux) {
		return ReactorSleuth.tracedFlux(this.tracer, this.currentTraceContext, "function", flux);
	}

}
