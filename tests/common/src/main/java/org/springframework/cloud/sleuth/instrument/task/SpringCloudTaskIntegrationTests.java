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

package org.springframework.cloud.sleuth.instrument.task;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.BDDAssertions.then;

@ContextConfiguration(classes = SpringCloudTaskIntegrationTests.TestConfig.class)
@TestPropertySource(properties = { "spring.application.name=MyApplication", "spring.sleuth.tx.enabled=false" })
public abstract class SpringCloudTaskIntegrationTests {

	@Autowired
	TestSpanHandler spans;

	@Test
	public void should_pass_tracing_information_when_using_spring_cloud_task() {
		Set<String> traceIds = this.spans.reportedSpans().stream().map(FinishedSpan::getTraceId)
				.collect(Collectors.toSet());
		then(traceIds).as("There's one traceid").hasSize(1);
		Set<String> spanIds = this.spans.reportedSpans().stream().map(FinishedSpan::getSpanId)
				.collect(Collectors.toSet());

		then(spanIds).as("There are 3 spans").hasSize(3);
		Iterator<FinishedSpan> spanIterator = this.spans.reportedSpans().iterator();

		FinishedSpan first = spanIterator.next();
		FinishedSpan second = spanIterator.next();
		FinishedSpan third = spanIterator.next();
		then(first.getName()).isEqualTo("myApplicationRunner");
		then(second.getName()).isEqualTo("myCommandLineRunner");
		then(third.getName()).isEqualTo("MyApplication");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EnableTask
	public static class TestConfig {

		@Bean
		MyCommandLineRunner myCommandLineRunner() {
			return new MyCommandLineRunner();
		}

		@Bean
		MyApplicationRunner myApplicationRunner() {
			return new MyApplicationRunner();
		}

	}

	static class MyCommandLineRunner implements CommandLineRunner {

		private static final Log log = LogFactory.getLog(MyCommandLineRunner.class);

		@Override
		public void run(String... args) throws Exception {
			log.info("Ran MyCommandLineRunner");
		}

	}

	static class MyApplicationRunner implements ApplicationRunner {

		private static final Log log = LogFactory.getLog(MyApplicationRunner.class);

		@Override
		public void run(ApplicationArguments args) throws Exception {
			log.info("Ran MyApplicationRunner");
		}

	}

}
