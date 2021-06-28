/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracer;
import org.springframework.cloud.sleuth.test.TestTracingBeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Oleg Zhurakousky
 *
 */
public abstract class TraceFunctionAroundWrapperTests {

	@Test
	public void test_tracing_with_supplier() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(configuration(),
				SampleConfiguration.class).run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true");) {
			TestSpanHandler spanHandler = context.getBean(TestSpanHandler.class);
			assertThat(spanHandler.reportedSpans()).isEmpty();
			FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = catalog.lookup("greeter");

			Message<?> result = (Message<?>) function.get();

			assertThat(result.getPayload()).isEqualTo("hello".getBytes());
			assertThat(spanHandler.reportedSpans().size()).isEqualTo(2);
			assertThat(((String) result.getHeaders().get("b3"))).contains(spanHandler.get(0).getTraceId());
			spanHandler.assertAllSpansWereFinishedOrAbandoned(context.getBean(TestTracer.class).createdSpans());
		}
	}

	@Test
	public void test_tracing_with_function() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(configuration(),
				SampleConfiguration.class).run("--logging.level.org.springframework.cloud.function=DEBUG",
						"--spring.main.lazy-initialization=true");) {
			TestSpanHandler spanHandler = context.getBean(TestSpanHandler.class);
			assertThat(spanHandler.reportedSpans()).isEmpty();
			FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
			FunctionInvocationWrapper function = catalog.lookup("uppercase");

			Message<?> result = (Message<?>) function.apply(MessageBuilder.withPayload("hello").build());

			assertThat(result.getPayload()).isEqualTo("HELLO");
			assertThat(spanHandler.reportedSpans().size()).isEqualTo(3);
			assertThat(((String) result.getHeaders().get("b3"))).contains(spanHandler.get(0).getTraceId());
			spanHandler.assertAllSpansWereFinishedOrAbandoned(context.getBean(TestTracer.class).createdSpans());
		}
	}

	protected abstract Class<?> configuration();

	@EnableAutoConfiguration
	public static class SampleConfiguration {

		@Bean
		public Supplier<String> greeter() {
			return () -> "hello";
		}

		@Bean
		public Function<String, String> uppercase() {
			return v -> v.toUpperCase();
		}

		@Bean
		static TestTracingBeanPostProcessor testTracerBeanPostProcessor() {
			return new TestTracingBeanPostProcessor();
		}

	}

};
