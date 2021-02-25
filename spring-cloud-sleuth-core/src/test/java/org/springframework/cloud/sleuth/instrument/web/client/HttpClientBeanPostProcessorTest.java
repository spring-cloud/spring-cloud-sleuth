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

package org.springframework.cloud.sleuth.instrument.web.client;

import brave.propagation.TraceContext;
import io.netty.bootstrap.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import org.springframework.cloud.sleuth.instrument.web.client.HttpClientBeanPostProcessor.PendingSpan;
import org.springframework.cloud.sleuth.instrument.web.client.HttpClientBeanPostProcessor.TracingMapConnect;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class HttpClientBeanPostProcessorTest {

	@Mock
	Connection connection;

	@Mock
	Bootstrap bootstrap;

	TraceContext traceContext = TraceContext.newBuilder().traceId(1).spanId(2)
			.sampled(true).build();

	@Test
	void mapConnect_should_setup_reactor_context_currentTraceContext() {
		TracingMapConnect tracingMapConnect = new TracingMapConnect(() -> traceContext);

		Mono<Connection> original = Mono.just(connection).handle((t, ctx) -> {
			assertThat(ctx.currentContext().get(TraceContext.class))
					.isSameAs(traceContext);
			assertThat(ctx.currentContext().get(PendingSpan.class)).isNotNull();
		});

		// Wrap and run the assertions
		tracingMapConnect.apply(original, bootstrap).log().subscribe();
	}

	@Test
	void mapConnect_should_setup_reactor_context_no_currentTraceContext() {
		TracingMapConnect tracingMapConnect = new TracingMapConnect(() -> null);

		Mono<Connection> original = Mono.just(connection).handle((t, ctx) -> {
			assertThat(ctx.currentContext().getOrEmpty(TraceContext.class)).isEmpty();
			assertThat(ctx.currentContext().get(PendingSpan.class)).isNotNull();
		});

		// Wrap and run the assertions
		tracingMapConnect.apply(original, bootstrap).log().subscribe();
	}

}
