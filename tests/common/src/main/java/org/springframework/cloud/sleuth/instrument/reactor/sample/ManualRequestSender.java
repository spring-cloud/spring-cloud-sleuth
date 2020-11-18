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

package org.springframework.cloud.sleuth.instrument.reactor.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.instrument.web.WebFluxSleuthOperators;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

class ManualRequestSender extends RequestSender {

	private static final Logger LOGGER = LoggerFactory.getLogger(ManualRequestSender.class);

	ManualRequestSender(WebClient webClient, Tracer tracer) {
		super(webClient, tracer);
	}

	@Override
	public Mono<String> get(Integer someParameterNotUsedNow) {
		return Mono.just(this.webClient).doOnEach(WebFluxSleuthOperators.withSpanInScope(SignalType.ON_NEXT, () -> {
			this.span = this.tracer.currentSpan();
			LOGGER.info("getting for parameter {}", someParameterNotUsedNow);
		})).flatMap(webClient -> Mono.subscriberContext()
				.flatMap(ctx -> WebFluxSleuthOperators.withSpanInScope(ctx, () -> webClient.method(HttpMethod.GET)
						.uri("http://localhost:" + port + "/foo").retrieve().bodyToMono(String.class))));
	}

	@Override
	public Flux<String> getAll() {
		return Flux.just("")
				.flatMap(s -> Flux.deferWithContext(ctx -> Flux.just("")
						.doOnNext(t -> WebFluxSleuthOperators.withSpanInScope(ctx, () -> LOGGER.info("before merge")))
						.mergeWith(get(2)).mergeWith(get(3))
						.doOnNext(t -> WebFluxSleuthOperators.withSpanInScope(ctx, () -> LOGGER.info("after merge")))));
	}

}
