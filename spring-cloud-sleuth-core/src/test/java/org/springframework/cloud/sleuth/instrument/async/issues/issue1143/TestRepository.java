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

import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;

import static reactor.core.publisher.Mono.just;

@Component
class TestRepository {

	<S extends TestEntity> Mono<S> save(S entity) {
		return just(entity);
	}

}
