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

package org.springframework.cloud.sleuth.instrument.reactor.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

class RequestSender {

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestSender.class);

	final WebClient webClient;

	final Tracer tracer;

	int port;

	Span span;

	RequestSender(WebClient webClient, Tracer tracer) {
		this.webClient = webClient;
		this.tracer = tracer;
	}

	public Mono<String> get(Integer someParameterNotUsedNow) {
		LOGGER.info("getting for parameter {}", someParameterNotUsedNow);
		this.span = this.tracer.currentSpan();
		return this.webClient.method(HttpMethod.GET).uri("http://localhost:" + this.port + "/foo").retrieve()
				.bodyToMono(String.class);
	}

	public Flux<String> getAll() {
		LOGGER.info("Before merge");
		Flux<String> merge = Flux.merge(get(1), get(2), get(3));
		LOGGER.info("after merge");
		return merge;
	}

}
