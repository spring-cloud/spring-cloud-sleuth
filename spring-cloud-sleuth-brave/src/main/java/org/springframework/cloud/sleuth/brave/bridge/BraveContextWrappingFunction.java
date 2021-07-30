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

package org.springframework.cloud.sleuth.brave.bridge;

import java.util.function.Function;

import reactor.util.context.Context;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;

/**
 * A function that wraps {@link Context} with Brave versions.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.4
 */
public class BraveContextWrappingFunction implements Function<Context, Context> {

	@Override
	public Context apply(Context context) {
		Span span = context.getOrDefault(Span.class, null);
		TraceContext traceContext = context.getOrDefault(TraceContext.class, null);
		if (span == null && traceContext == null) {
			return context;
		}
		if (context.hasKey(brave.propagation.TraceContext.class)) {
			return context;
		}
		return mutateContextWithBrave(context, span, traceContext);
	}

	private Context mutateContextWithBrave(Context context, Span span, TraceContext traceContext) {
		brave.Span braveSpan = BraveSpan.toBrave(span);
		brave.propagation.TraceContext braveTraceContext = BraveTraceContext.toBrave(traceContext);
		Context mutatedContext = context;
		if (braveSpan != null) {
			mutatedContext = context.put(brave.Span.class, braveSpan);
		}
		if (braveTraceContext != null) {
			mutatedContext = mutatedContext.put(brave.propagation.TraceContext.class, braveTraceContext);
		}
		return mutatedContext;
	}

}
