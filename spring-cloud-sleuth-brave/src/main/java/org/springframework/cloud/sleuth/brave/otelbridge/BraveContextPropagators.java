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

package org.springframework.cloud.sleuth.brave.otelbridge;

import java.util.List;

import brave.Tracer;
import brave.propagation.Propagation;
import brave.propagation.TraceContextOrSamplingFlags;
import io.grpc.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.TracingContextUtils;

/**
 * Brave version of {@link ContextPropagators}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class BraveContextPropagators implements ContextPropagators {

	private final ContextPropagators contextPropagators;

	public BraveContextPropagators(Propagation.Factory factory, Tracer tracer) {
		this(factory.get(), tracer);
	}

	public BraveContextPropagators(Propagation<String> propagation, Tracer tracer) {
		this.contextPropagators = DefaultContextPropagators.builder().addTextMapPropagator(new TextMapPropagator() {
			@Override
			public List<String> fields() {
				return propagation.keys();
			}

			@Override
			public <C> void inject(Context context, @javax.annotation.Nullable C carrier, Setter<C> setter) {
				Span span = TracingContextUtils.getSpanWithoutDefault(context);
				if (span == null) {
					throw new IllegalStateException("Can't inject to a null span");
				}
				BraveSpanContext spanContext = (BraveSpanContext) span.getContext();
				propagation.injector(setter::set).inject(spanContext.unwrap(), carrier);
			}

			@Override
			public <C> Context extract(Context context, C carrier, Getter<C> getter) {
				TraceContextOrSamplingFlags extract = propagation.extractor(getter::get).extract(carrier);
				brave.Span span = tracer.nextSpan(extract).start();
				BraveSpan braveSpan = new BraveSpan(span);
				return TracingContextUtils.withSpan(braveSpan, context);
			}
		}).build();
	}

	@Override
	public TextMapPropagator getTextMapPropagator() {
		return this.contextPropagators.getTextMapPropagator();
	}

}
