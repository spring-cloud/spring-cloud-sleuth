/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.async.issues.issue1143;

import brave.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Component
class TestHandler {

	private static final Logger log = LoggerFactory.getLogger(TestHandler.class);

	private final TestRepository repository;

	private final Tracer tracer;

	TestHandler(TestRepository repository, Tracer tracer) {
		this.repository = repository;
		this.tracer = tracer;
	}

	Mono<ServerResponse> save(ServerRequest request) {
		log.info("HELLO 0");
		return request.bodyToMono(TestEntity.class).map(testEntity -> {
			log.info("HELLO 1 " + this.tracer.currentSpan());
			return testEntity.setContext();
		}).flatMap(entity1 -> {
			log.info("HELLO 2 " + this.tracer.currentSpan());
			return this.repository.save(entity1);
		}).flatMap(entity -> {
			log.info("HELLO 3 " + this.tracer.currentSpan());
			return ok().build();
		});
	}

}
