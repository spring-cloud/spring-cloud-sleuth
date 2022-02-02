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

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.BDDAssertions.then;

@ContextConfiguration(classes = R2dbcIntegrationTests.TestConfig.class)
@TestPropertySource(properties = { "spring.application.name=MyApplication",
		"spring.sleuth.reactor.instrumentation-type=decorate_queues" })
public abstract class R2dbcIntegrationTests {

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracer tracer;

	@Test
	public void should_pass_tracing_information_when_using_r2dbc() {
		Set<String> traceIds = this.spans.reportedSpans().stream().map(FinishedSpan::getTraceId)
				.collect(Collectors.toSet());
		then(traceIds).as("There's one traceid").hasSize(1);
		Set<String> spanIds = this.spans.reportedSpans().stream().map(FinishedSpan::getSpanId)
				.collect(Collectors.toSet());

		// 2 transactions - 9 database interactions
		then(spanIds).as("There are 11 spans").hasSize(11);
		List<String> spanNames = this.spans.reportedSpans().stream().map(FinishedSpan::getName)
				.collect(Collectors.toList());
		List<String> remoteServiceNames = this.spans.reportedSpans().stream().map(FinishedSpan::getRemoteServiceName)
				.collect(Collectors.toList());
		then(spanNames.stream().filter("tx"::equalsIgnoreCase).collect(Collectors.toList())).hasSize(2);
		then(remoteServiceNames.stream().filter("h2"::equalsIgnoreCase).collect(Collectors.toList())).hasSize(9);
		then(tracer.currentSpan()).isNull();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@ComponentScan
	public static class TestConfig {

		private static final Logger log = LoggerFactory.getLogger(TestConfig.class);

		@Bean
		public CommandLineRunner demo(ReactiveNewTransactionService reactiveNewTransactionService) {
			return (args) -> {
				try {
					reactiveNewTransactionService.newTransaction().block(Duration.ofSeconds(50));
				}
				catch (DataAccessException e) {
					log.info("Expected to throw an exception so that we see if rollback works", e);
				}
			};
		}

	}

}
