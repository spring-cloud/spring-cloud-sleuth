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

package org.springframework.cloud.sleuth.instrument.async.issues.issue1212;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.junit.Test;
import org.slf4j.LoggerFactory;

import org.springframework.aop.interceptor.AsyncExecutionAspectSupport;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.annotation.EnableAsync;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Bertrand Renuart
 */
public class GH1212Tests {

	@Test
	public void defaultTaskExecutor() throws Exception {
		try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(App.class,
				DefaultTaskExecutorConfig.class).run()) {
			String asyncThreadName = getAsyncThreadName(ctx);
			assertThat(asyncThreadName).startsWith("defaultTaskExecutor");
		}
	}

	@Test
	public void singleTaskExecutor() throws Exception {
		try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(App.class,
				SingleTaskExecutorConfig.class).run()) {
			String asyncThreadName = getAsyncThreadName(ctx);
			assertThat(asyncThreadName).startsWith("singleTaskExecutor");
		}
	}

	@Test
	public void multipleTaskExecutors() throws Exception {
		try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(App.class,
				MultipleTaskExecutorConfig.class).run()) {
			String asyncThreadName = getAsyncThreadName(ctx);
			assertThat(asyncThreadName).doesNotStartWith("multipleTaskExecutor");
			assertThat(asyncThreadName).startsWith("SimpleAsyncTaskExecutor"); // <--
																				// comes
																				// from
																				// Sleuth's
																				// own
																				// AsyncConfigurer
		}
	}

	@Test
	public void customAsyncConfigurer() throws Exception {
		try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(App.class,
				CustomAsyncConfigurerConfig.class).run()) {
			String asyncThreadName = getAsyncThreadName(ctx);
			assertThat(asyncThreadName).startsWith("customAsyncConfigurer");
		}
	}

	private String getAsyncThreadName(ApplicationContext ctx) throws Exception {
		return ctx.getBean(AsyncComponent.class).asyncMethod().get();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EnableAsync
	static class App {

		@Bean
		AsyncComponent asyncComponent() {
			return new AsyncComponent();
		}

	}

	static class AsyncComponent {

		@Async
		public CompletableFuture<String> asyncMethod() {
			LoggerFactory.getLogger("test").info("asyncMethod invoked");
			return CompletableFuture.completedFuture(Thread.currentThread().getName());
		}

	}

	/*
	 * Configuration with a single Executor named `taskExecutor`
	 */
	@Configuration
	static class DefaultTaskExecutorConfig {

		@Bean(name = AsyncExecutionAspectSupport.DEFAULT_TASK_EXECUTOR_BEAN_NAME)
		public Executor taskExecutor() {
			return new SimpleAsyncTaskExecutor("defaultTaskExecutor");
		}

	}

	/*
	 * Configuration with a single TaskExecutor
	 */
	@Configuration
	static class SingleTaskExecutorConfig {

		@Bean
		// there's the task
		@Primary
		public TaskExecutor singleTaskExecutor() {
			return new SimpleAsyncTaskExecutor("singleTaskExecutor");
		}

	}

	/*
	 * Configuration with a multiple TaskExecutors --> Spring won't pick any unless one
	 * is @Primary
	 */
	@Configuration
	static class MultipleTaskExecutorConfig {

		@Bean
		public TaskExecutor multipleTaskExecutor1() {
			return new SimpleAsyncTaskExecutor("multipleTaskExecutor1");
		}

		@Bean
		public TaskExecutor multipleTaskExecutor2() {
			return new SimpleAsyncTaskExecutor("multipleTaskExecutor2");
		}

	}

	/*
	 * Configuration where a custom AsyncConfigurer is provided
	 */
	@Configuration
	static class CustomAsyncConfigurerConfig {

		@Bean
		public AsyncConfigurer customAsyncConfigurer() {
			return new AsyncConfigurerSupport() {
				@Override
				public Executor getAsyncExecutor() {
					return new SimpleAsyncTaskExecutor("customAsyncConfigurer");
				}
			};
		}

	}

}
