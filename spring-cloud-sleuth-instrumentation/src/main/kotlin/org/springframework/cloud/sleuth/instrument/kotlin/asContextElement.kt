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

package org.springframework.cloud.sleuth.instrument.kotlin

import kotlinx.coroutines.reactor.ReactorContext
import org.springframework.cloud.sleuth.CurrentTraceContext
import org.springframework.cloud.sleuth.Span
import org.springframework.cloud.sleuth.TraceContext
import org.springframework.cloud.sleuth.Tracer
import org.springframework.util.ClassUtils
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.jvm.internal.impl.load.kotlin.KotlinClassFinder

/**
 * Returns a [CoroutineContext] which will make this [Context] current when resuming a coroutine
 * and restores the previous [Context] on suspension.
 *
 * Inspired by OpenTelemetry's asContextElement.
 * @since 3.1.0
 */
fun Tracer.asContextElement(): CoroutineContext {
	return KotlinContextElement(this)
}

/**
 * Returns the [Span] in this [CoroutineContext] if present, or null otherwise.
 *
 * Inspired by OpenTelemetry's asContextElement.
 * @since 3.1.0
 */
fun CoroutineContext.currentSpan(): Span? {
	val element = get(KotlinContextElement.KEY)
	if (element is KotlinContextElement) {
		return element.span
	}
	if (!ClassUtils.isPresent("kotlinx.coroutines.reactor.ReactorContext", null)) {
		return null
	}
	val reactorContext = get(ReactorContext.Key)
	if (reactorContext != null) {
		if (reactorContext.context.hasKey(Span::class.java)) {
			return reactorContext.context.get(Span::class.java)
		}
		else if (reactorContext.context.hasKey(TraceContext::class.java) && reactorContext.context.hasKey(Tracer::class.java) && reactorContext.context.hasKey(CurrentTraceContext::class.java)) {
			val traceContext = reactorContext.context.get(TraceContext::class.java)
			reactorContext.context.get(CurrentTraceContext::class.java).maybeScope(traceContext).use {
				return reactorContext.context.get(Tracer::class.java).currentSpan()
			}
		}
		else if (reactorContext.context.hasKey(Tracer::class.java)) {
			return reactorContext.context.get(Tracer::class.java).currentSpan()
		}
	}
	return null
}
