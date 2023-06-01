/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.instrument.reactor.netty;

import java.util.concurrent.atomic.AtomicReference;

import brave.propagation.TraceContext;
import reactor.util.context.ContextView;

import org.springframework.cloud.sleuth.brave.bridge.BraveTraceContext;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;

final class TracingHandlerUtil {

	private TracingHandlerUtil() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	static TraceContext traceContext(ContextView ctxView) {
		AtomicReference<org.springframework.cloud.sleuth.Span> pendingSpan = ReactorSleuth.getPendingSpan(ctxView);
		if (pendingSpan != null) {
			org.springframework.cloud.sleuth.Span span = pendingSpan.get();
			if (span != null) {
				return BraveTraceContext.toBrave(span.context());
			}
		}
		return null;
	}

}
