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

package org.springframework.cloud.sleuth.autoconfig.brave.baggage;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.cloud.sleuth.DisableWebFluxSecurity;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.autoconfig.instrument.reactor.TraceReactorAutoConfiguration;
import org.springframework.cloud.sleuth.autoconfig.instrument.web.TraceWebAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.core.IsEqual.equalTo;

@WebFluxTest(controllers = TracingResource.class, properties = "spring.main.web-application-type=reactive")
@ImportAutoConfiguration({ BraveAutoConfiguration.class, TraceWebAutoConfiguration.class,
		TraceReactorAutoConfiguration.class })
@Import(TracingResource.class)
@DisableWebFluxSecurity
public class CompositePropagationFactoryTests {

	@Autowired
	TracingResource tracingResource;

	@Test
	void should_delegate_configuration_to_propagation_factory(@Autowired WebTestClient webTestClient) {
		// issue 1846 - without the fix supportJoin is assumed to be false
		// because it's not taken from the configuration but from not overridden
		// methods from CompositePropagationFactorySupplier
		String spanId = "a2fb4a1d1a96d312";
		webTestClient.get().uri("/api/tracing/spanId").header("X-B3-TraceId", "463ac35c9f6413ad48485a3953bb6124")
				.header("X-B3-SpanId", spanId).header("X-B3-ParentSpanId", "0020000000000001").header("X-B3-Flags", "1")
				.exchange().expectStatus().isOk().expectBody(String.class)
				.value(returnedSpanId -> returnedSpanId, equalTo(spanId));

	}

}

@RestController
@RequestMapping("/api/tracing")
class TracingResource {

	private static final Class<TraceContext> KEY = TraceContext.class;

	@GetMapping("spanId")
	public Mono<String> spanId() {
		return Mono.deferContextual(view -> traceContext(view)).map(c -> c.spanId());

	}

	private Mono<TraceContext> traceContext(ContextView contextView) {
		return Mono.justOrEmpty(contextView.get(KEY));
	}

}
