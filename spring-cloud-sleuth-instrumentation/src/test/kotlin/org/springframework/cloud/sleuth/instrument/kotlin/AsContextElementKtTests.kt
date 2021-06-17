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
import org.springframework.cloud.sleuth.Span
import org.springframework.cloud.sleuth.Tracer
import org.springframework.cloud.sleuth.tracer.SimpleTracer
import reactor.util.context.Context

internal class AsContextElementKtTests {

	@Test
	fun `should return current span from context`(): Unit = runBlocking {
		val simpleTracer = SimpleTracer()
		val nextSpan = simpleTracer.nextSpan().start()
		var spanInGlobalScopeLaunch: Span? = null
		var spanInGlobalScopeAsync: Span? = null
		val asContextElement = simpleTracer.asContextElement()

		GlobalScope.launch(asContextElement) {
			spanInGlobalScopeLaunch = coroutineContext.getCurrentSpan()
		}
		GlobalScope.async(asContextElement) {
			spanInGlobalScopeAsync = coroutineContext.getCurrentSpan()
		}.await()

		then(spanInGlobalScopeLaunch).isSameAs(nextSpan)
		then(spanInGlobalScopeAsync).isSameAs(nextSpan)
	}

	@Test
	fun `should return span from coroutine context when KotlinContextElement present`(): Unit = runBlocking {
		val simpleTracer = SimpleTracer()
		val nextSpan = simpleTracer.nextSpan().start()
		val element = KotlinContextElement(simpleTracer)

		then(element.getCurrentSpan()).isSameAs(nextSpan)
	}

	@Test
	fun `should return null from coroutine context when KotlinContextElement and Reactor extensions are missing`(): Unit = runBlocking {
		val contextClassLoader = Thread.currentThread().contextClassLoader
		try {
			Thread.currentThread().contextClassLoader = FilteredClassLoader("kotlinx.coroutines.reactor.ReactorContext")
			then(coroutineContext.getCurrentSpan()).isNull()
		} finally {
			Thread.currentThread().contextClassLoader = contextClassLoader
		}
	}

	@Test
	fun `should return Span from Reactor extensions when KotlinContextElement missing`(): Unit = runBlocking {
		val simpleTracer = SimpleTracer()
		val nextSpan = simpleTracer.nextSpan().start()
		val reactorContext = ReactorContext(Context.of(Span::class.java, nextSpan))

		then(reactorContext.getCurrentSpan()).isSameAs(nextSpan);
	}

	@Test
	fun `should return Span from Reactor extensions Tracer when KotlinContextElement missing and there is no Span in context`(): Unit = runBlocking {
		val simpleTracer = SimpleTracer()
		val nextSpan = simpleTracer.nextSpan().start()
		val reactorContext = ReactorContext(Context.of(Tracer::class.java, simpleTracer))

		then(reactorContext.getCurrentSpan()).isSameAs(nextSpan);
	}

	@Test
	fun `should return null when no span is found`(): Unit = runBlocking {
		val reactorContext = ReactorContext(Context.empty())

		then(reactorContext.getCurrentSpan()).isNull()
	}

}
