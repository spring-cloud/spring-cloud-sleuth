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

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.BDDAssertions.then
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.cloud.sleuth.CurrentTraceContext
import org.springframework.cloud.sleuth.Span
import org.springframework.cloud.sleuth.TraceContext
import org.springframework.cloud.sleuth.Tracer
import org.springframework.cloud.sleuth.tracer.SimpleCurrentTraceContext
import org.springframework.cloud.sleuth.tracer.SimpleSpan
import org.springframework.cloud.sleuth.tracer.SimpleTracer
import reactor.util.context.Context

internal class AsContextElementKtTests {

	@Test
	fun `should return current span from context`(): Unit = runBlocking {
		val simpleTracer = SimpleTracer()
		val nextSpan = simpleTracer.nextSpan().start()
		val inScope = simpleTracer.withSpan(nextSpan)
		var spanInGlobalScopeLaunch: Span? = null
		var spanInGlobalScopeAsync: Span? = null
		val asContextElement = simpleTracer.asContextElement()

		GlobalScope.launch(asContextElement) {
			spanInGlobalScopeLaunch = coroutineContext.currentSpan()
			then((spanInGlobalScopeLaunch as SimpleSpan).ended).isFalse()
		}
		GlobalScope.async(asContextElement) {
			spanInGlobalScopeAsync = coroutineContext.currentSpan()
			then((spanInGlobalScopeAsync as SimpleSpan).ended).isFalse()
		}.await()

		inScope.close();

		then(spanInGlobalScopeLaunch).isSameAs(nextSpan)
		then(spanInGlobalScopeAsync).isSameAs(nextSpan)
		then((spanInGlobalScopeLaunch as SimpleSpan).ended).isFalse()
		then((spanInGlobalScopeAsync as SimpleSpan).ended).isFalse()
	}

	@Test
	fun `should return span from coroutine context when KotlinContextElement present`(): Unit = runBlocking {
		val simpleTracer = SimpleTracer()
		val nextSpan = simpleTracer.nextSpan().start()
		val inScope = simpleTracer.withSpan(nextSpan)
		val element = KotlinContextElement(simpleTracer)

		then(element.currentSpan()).isSameAs(nextSpan)
		inScope.close()
	}

	@Test
	fun `should return null from coroutine context when KotlinContextElement and Reactor extensions are missing`(): Unit = runBlocking {
		val contextClassLoader = Thread.currentThread().contextClassLoader
		try {
			Thread.currentThread().contextClassLoader = FilteredClassLoader("kotlinx.coroutines.reactor.ReactorContext")
			then(coroutineContext.currentSpan()).isNull()
		} finally {
			Thread.currentThread().contextClassLoader = contextClassLoader
		}
	}

	@Test
	fun `should return Span from Reactor extensions when KotlinContextElement missing`(): Unit = runBlocking {
		val simpleTracer = SimpleTracer()
		val nextSpan = simpleTracer.nextSpan().start()
		val reactorContext = ReactorContext(Context.of(Span::class.java, nextSpan))

		then(reactorContext.currentSpan()).isSameAs(nextSpan);
	}

	@Test
	fun `should return Span from Reactor extensions CurrentTraceContext when KotlinContextElement missing and there is TraceContext in Reactor context`(): Unit = runBlocking {
		val currentTraceContext = SimpleCurrentTraceContext()
		val simpleTracer = SimpleTracer()
		val nextSpan = simpleTracer.nextSpan().start()
		val inScope = simpleTracer.withSpan(nextSpan)
		val reactorContext = ReactorContext(Context.of(Tracer::class.java, simpleTracer, CurrentTraceContext::class.java, currentTraceContext, TraceContext::class.java, nextSpan.context()))

		then(reactorContext.currentSpan()).isSameAs(nextSpan);
		inScope.close()
	}

	@Test
	fun `should return Span from Reactor extensions Tracer when KotlinContextElement missing and there is no Span in context`(): Unit = runBlocking {
		val simpleTracer = SimpleTracer()
		val nextSpan = simpleTracer.nextSpan().start()
		val reactorContext = ReactorContext(Context.of(Tracer::class.java, simpleTracer))
		val inScope = simpleTracer.withSpan(nextSpan)

		then(reactorContext.currentSpan()).isSameAs(nextSpan);
		inScope.close()
	}

	@Test
	fun `should return null when no span is found`(): Unit = runBlocking {
		val reactorContext = ReactorContext(Context.empty())

		then(reactorContext.currentSpan()).isNull()
	}

}
