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

package org.springframework.cloud.sleuth.instrument.r2dbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReactiveNestedTransactionService {

	private static final Logger log = LoggerFactory.getLogger(ReactiveNestedTransactionService.class);

	private final ReactiveCustomerRepository repository;

	public ReactiveNestedTransactionService(ReactiveCustomerRepository repository) {
		this.repository = repository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Mono<Void> requiresNew() {
		return Mono.fromRunnable(() -> log.info("Hello from nested transaction"))
				.then(repository.save(new ReactiveCustomer("Hello", "From Propagated Transaction"))).then();
	}

}
