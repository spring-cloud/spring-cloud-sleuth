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

package org.springframework.cloud.sleuth.otel.instrument.messaging;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import io.grpc.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extensions.trace.propagation.B3Propagator;

import org.springframework.cloud.sleuth.otel.OtelTestTracing;
import org.springframework.cloud.sleuth.test.TestTracingAware;

public class TracingChannelInterceptorTest
		extends org.springframework.cloud.sleuth.instrument.messaging.TracingChannelInterceptorTest {

	OtelTestTracing testTracing;

	@Override
	public TestTracingAware tracerTest() {
		if (this.testTracing == null) {
			this.testTracing = new OtelTestTracing() {
				@Override
				protected ContextPropagators contextPropagators() {
					return DefaultContextPropagators.builder().addTextMapPropagator(new TextMapPropagator() {
						@Override
						public List<String> fields() {
							return Collections.singletonList("b3");
						}

						@Override
						public <C> void inject(Context context, @Nullable C c, Setter<C> setter) {
							B3Propagator.getSingleHeaderPropagator().inject(context, c, setter);
						}

						@Override
						public <C> Context extract(Context context, C c, Getter<C> getter) {
							return B3Propagator.getSingleHeaderPropagator().extract(context, c, getter);
						}
					}).build();
				}
			};
		}
		return this.testTracing;
	}

}
