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

package org.springframework.cloud.sleuth.benchmarks.app.stream;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootApplication
@Import(TestChannelBinderConfiguration.class)
public class SleuthBenchmarkingStreamApplication {

	private static final Logger log = LoggerFactory.getLogger(SleuthBenchmarkingStreamApplication.class);

	public static void main(String[] args) throws InterruptedException, IOException {
		// System.setProperty("spring.sleuth.enabled", "false");
		// System.setProperty("spring.sleuth.reactor.instrumentation-type",
		// "DECORATE_ON_EACH");
		// System.setProperty("spring.sleuth.reactor.instrumentation-type",
		// "DECORATE_ON_LAST");
		// System.setProperty("spring.sleuth.reactor.instrumentation-type", "MANUAL");
		System.setProperty("spring.sleuth.reactor.instrumentation-type", "MANUAL");
		System.setProperty("spring.sleuth.function.type", "simple");
		ConfigurableApplicationContext context = SpringApplication.run(SleuthBenchmarkingStreamApplication.class, args);
		for (int i = 0; i < 1; i++) {
			InputDestination input = context.getBean(InputDestination.class);
			input.send(MessageBuilder.withPayload("hello".getBytes())
					.setHeader("b3", "4883117762eb9420-4883117762eb9420-1").build());
			log.info("Retrieving the message for tests");
			OutputDestination output = context.getBean(OutputDestination.class);
			Message<byte[]> message = output.receive(200L);
			log.info("Got the message from output");
			assertThat(message).isNotNull();
			log.info("Message is not null");
			assertThat(message.getPayload()).isEqualTo("HELLO".getBytes());
			log.info("Payload is HELLO");
			String b3 = message.getHeaders().get("b3", String.class);
			log.info("Checking the b3 header [" + b3 + "]");
			assertThat(b3).startsWith("4883117762eb9420");
		}
	}

	@Bean
	ExecutorService sleuthExecutorService() {
		return Executors.newCachedThreadPool();
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "simple")
	public Function<String, String> simple() {
		log.info("simple_function");
		return new SimpleFunction();
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "reactive_simple")
	public Function<Flux<String>, Flux<String>> reactiveSimple() {
		log.info("simple_reactive_function");
		return new SimpleReactiveFunction();
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "simple_function_with_around")
	public Function<Message<String>, Message<String>> simpleFunctionWithAround() {
		log.info("simple_function_with_around");
		return new SimpleMessageFunction();
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.nonreactive.function.enabled", havingValue = "true")
	public Function<String, String> nonReactiveFunction(ExecutorService executorService) {
		log.info("no sleuth non reactive function");
		return new SleuthNonReactiveFunction(executorService);
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "DECORATE_ON_EACH",
			matchIfMissing = true)
	public Function<Flux<String>, Flux<String>> onEachFunction() {
		log.info("on each function");
		return new SleuthFunction();
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "DECORATE_ON_LAST")
	public Function<Flux<String>, Flux<String>> onLastFunction() {
		log.info("on last function");
		return new SleuthFunction();
	}

}

class SimpleFunction implements Function<String, String> {

	private static final Logger log = LoggerFactory.getLogger(SimpleFunction.class);

	@Override
	public String apply(String input) {
		// tracing works cause headers from the input message get propagated to the output
		// message
		log.info("Hello from simple [{}]", input);
		return input.toUpperCase();
	}

}

class SimpleReactiveFunction implements Function<Flux<String>, Flux<String>> {

	private static final Logger log = LoggerFactory.getLogger(SimpleReactiveFunction.class);

	@Override
	public Flux<String> apply(Flux<String> input) {
		return input.doOnNext(s -> log.info("Hello from simple [{}]", s)).map(String::toUpperCase);
	}

}

class SimpleMessageFunction implements Function<Message<String>, Message<String>> {

	private static final Logger log = LoggerFactory.getLogger(SimpleFunction.class);

	@Override
	public Message<String> apply(Message<String> input) {
		log.info("Hello from message simple [{}]", input.getPayload());
		return MessageBuilder.withPayload(input.getPayload().toUpperCase()).build();
	}

}

class SleuthNonReactiveFunction implements Function<String, String> {

	private static final Logger log = LoggerFactory.getLogger(SleuthNonReactiveFunction.class);

	private final ExecutorService executorService;

	SleuthNonReactiveFunction(ExecutorService executorService) {
		this.executorService = executorService;
	}

	@Override
	public String apply(String input) {
		log.info("Got a message");
		try {
			return this.executorService.submit(() -> {
				log.info("Logging [{}] from a new thread", input);
				return input.toUpperCase();
			}).get(20, TimeUnit.MILLISECONDS);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}

class SleuthFunction implements Function<Flux<String>, Flux<String>> {

	private static final Logger log = LoggerFactory.getLogger(SleuthFunction.class);

	static final Scheduler SCHEDULER = Schedulers.newParallel("sleuthFunction");

	@Override
	public Flux<String> apply(Flux<String> input) {
		return input.doOnEach(signal -> log.info("Got a message"))
				.flatMap(s -> Mono.delay(Duration.ofMillis(1), SCHEDULER).map(aLong -> {
					log.info("Logging [{}] from flat map", s);
					return s.toUpperCase();
				}));
	}

}
