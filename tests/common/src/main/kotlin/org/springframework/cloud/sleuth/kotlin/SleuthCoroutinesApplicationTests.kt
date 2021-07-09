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

package org.springframework.cloud.sleuth.kotlin

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.BDDAssertions.then
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.cloud.sleuth.Span
import org.springframework.cloud.sleuth.Tracer
import org.springframework.cloud.sleuth.instrument.kotlin.asContextElement
import org.springframework.cloud.sleuth.instrument.kotlin.currentSpan
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import kotlin.coroutines.coroutineContext

@ContextConfiguration(classes = [SleuthCoroutinesApplicationTests.ControllerTestConfig::class])
abstract class SleuthCoroutinesApplicationTests {

	@LocalServerPort
	var serverPort: Int = 0

	@Autowired
	lateinit var tracer: Tracer

	@Autowired
	lateinit var webController: WebController

	@Autowired
	lateinit var restTemplate: RestTemplate

	@Test
	fun should_pass_tracing_context_within_coroutines(): Unit = runBlocking {
		val nextSpan = tracer.nextSpan()
		val traceId = nextSpan.context().traceId()
		tracer.withSpan(nextSpan.start()).use { withSpan ->
			val response = restTemplate.getForObject(applicationUrl(), String::class.java)
			// span in the controller is set
			then(webController.spanInController).isNotNull
			then(webController.spanInController?.context()?.traceId()).isEqualTo(traceId)
			// span in global scope launch is set
			then(webController.spanInGlobalScopeLaunch).isNotNull
			then(webController.spanInGlobalScopeLaunch?.context()?.traceId()).isEqualTo(traceId)
			// span in global async scope is set
			then(webController.spanInGlobalScopeAsync).isNotNull
			then(webController.spanInGlobalScopeAsync?.context()?.traceId()).isEqualTo(traceId)
			// trace id got propagated
			then(response).isEqualTo(traceId)
		}
	}

	private fun applicationUrl() = "http://localhost:$serverPort/hello"

	@TestConfiguration
	@EnableAutoConfiguration
	internal class ControllerTestConfig {
		@Bean
		fun restTemplate(): RestTemplate = RestTemplate()

		@Bean
		fun webController(tracer: Tracer): WebController = WebController(tracer)
	}
}

@RestController
class WebController(val tracer: Tracer) {
	val log: Logger = LoggerFactory.getLogger(this::class.java)
	var spanInController: Span? = null
	var spanInGlobalScopeLaunch: Span? = null
	var spanInGlobalScopeAsync: Span? = null

	@GetMapping("/hello")
	suspend fun hello(): String? {
		spanInController = coroutineContext.currentSpan()
		GlobalScope.launch(tracer.asContextElement()) {
			log.info("in Coroutines context (launch) - current span {}", coroutineContext.currentSpan())
			spanInGlobalScopeLaunch = coroutineContext.currentSpan()
		}
		GlobalScope.async(tracer.asContextElement()) {
			log.info("in Coroutines context (async)- current span {}", coroutineContext.currentSpan())
			spanInGlobalScopeAsync = coroutineContext.currentSpan()
		}.await()
		return coroutineContext.currentSpan()?.context()?.traceId()
	}
}
