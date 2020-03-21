/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import brave.Tracer;
import brave.http.HttpTracing;
import brave.propagation.ExtraFieldPropagation;
import brave.test.http.ITHttp;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RestController
class WebFluxController {

	final Tracer tracer;

	@Autowired
	WebFluxController(HttpTracing httpTracing) {
		this.tracer = httpTracing.tracing().tracer();
	}

	@GetMapping("/async")
	public Mono<String> async(ServerRequest req) {
		return Mono.defer(this::foo);
	}

	@GetMapping("/exceptionAsync")
	public Mono<String> notReadyAsync(ServerHttpResponse response) {
		return Mono.defer(this::notReady);
	}

	@GetMapping("/async_items/{itemId}")
	public Mono<String> asyncItems(@PathVariable("itemId") String itemId) {
		return Mono.defer(() -> items(itemId));
	}

	@RequestMapping(method = RequestMethod.OPTIONS, value = "/")
	public Mono<String> root() {
		return Mono.just("ok");
	}

	@GetMapping("/foo")
	public Mono<String> foo() {
		return Mono.just("ok");
	}

	@GetMapping("/extra")
	public Mono<String> extra() {
		return Mono.just(ExtraFieldPropagation.get(ITHttp.EXTRA_KEY));
	}

	@GetMapping("/badrequest")
	@ResponseStatus(BAD_REQUEST)
	public Mono<String> badrequest() {
		return Mono.empty();
	}

	@GetMapping("/child")
	public Mono<ServerResponse> child() {
		tracer.nextSpan().name("child").start().finish();
		return ServerResponse.ok().build();
	}

	@GetMapping("/exception")
	public Mono<String> notReady() {
		return Mono.error(new IllegalStateException());
	}

	@ResponseStatus(value = SERVICE_UNAVAILABLE, reason = "not ready")
	@ExceptionHandler(IllegalStateException.class)
	public void IllegalStateExceptionHandler() {
	}

	@GetMapping("/items/{itemId}")
	public Mono<String> items(@PathVariable("itemId") String itemId) {
		return Mono.just(itemId);
	}

	@RestController
	@RequestMapping("/nested")
	static class NestedController {

		@GetMapping("/items/{itemId}")
		public Mono<String> items(@PathVariable("itemId") String itemId) {
			return Mono.just(itemId);
		}

	}

}
