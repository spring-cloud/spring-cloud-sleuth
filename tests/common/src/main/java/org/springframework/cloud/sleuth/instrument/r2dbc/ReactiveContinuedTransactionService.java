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
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReactiveContinuedTransactionService {

	private static final Logger log = LoggerFactory.getLogger(ReactiveContinuedTransactionService.class);

	private final ReactiveCustomerRepository repository;

	private final ReactiveNestedTransactionService reactiveNestedTransactionService;

	public ReactiveContinuedTransactionService(ReactiveCustomerRepository repository,
			ReactiveNestedTransactionService reactiveNestedTransactionService) {
		this.repository = repository;
		this.reactiveNestedTransactionService = reactiveNestedTransactionService;
	}

	@Transactional
	public Mono<Void> continuedTransaction() {
		return Mono.fromRunnable(() -> log.info("Hello from continued transaction")).then(repository.findById(1L))
				.doOnNext(customer -> {
					// fetch an individual customer by ID
					log.info("Customer found with findById(1L):");
					log.info("--------------------------------");
					log.info(customer.toString());
					log.info("");
				}).doOnNext(customer -> {
					// fetch customers by last name
					log.info("Customer found with findByLastName('Bauer'):");
					log.info("--------------------------------------------");
				}).flatMapMany(customer -> repository.findByLastName("Bauer"))
				.doOnNext(cust -> log.info(cust.toString())).then(this.reactiveNestedTransactionService.requiresNew());
	}

}
