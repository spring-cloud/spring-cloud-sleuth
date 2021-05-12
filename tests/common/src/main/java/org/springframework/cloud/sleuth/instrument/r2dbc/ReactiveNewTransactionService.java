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
public class ReactiveNewTransactionService {

	private static final Logger log = LoggerFactory.getLogger(ReactiveNewTransactionService.class);

	private final ReactiveCustomerRepository repository;

	private final ReactiveContinuedTransactionService reactiveContinuedTransactionService;

	public ReactiveNewTransactionService(ReactiveCustomerRepository repository,
			ReactiveContinuedTransactionService reactiveContinuedTransactionService) {
		this.repository = repository;
		this.reactiveContinuedTransactionService = reactiveContinuedTransactionService;
	}

	// 6 database transactions
	@Transactional
	public Mono<Void> newTransaction() {
		return Mono.fromRunnable(() -> log.info("Hello from new transaction"))
				// save a few customers
				.then(repository.save(new ReactiveCustomer("Jack", "Bauer")))
				.then(repository.save(new ReactiveCustomer("Chloe", "O'Brian")))
				.then(repository.save(new ReactiveCustomer("Kim", "Bauer")))
				.then(repository.save(new ReactiveCustomer("David", "Palmer")))
				.then(repository.save(new ReactiveCustomer("Michelle", "Dessler"))).doOnNext(reactiveCustomer -> {
					log.info("Customers found with findAll():");
					log.info("-------------------------------");
				}).flatMapMany(reactiveCustomer -> repository.findAll()).doOnNext(cust -> log.info(cust.toString()))
				.doOnNext(o -> log.info("")).then(this.reactiveContinuedTransactionService.continuedTransaction());
	}

}
