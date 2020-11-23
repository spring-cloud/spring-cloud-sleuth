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

package org.springframework.cloud.sleuth.benchmarks.app.webflux;

import java.time.Duration;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.context.ReactiveWebServerInitializedEvent;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.cloud.sleuth.instrument.web.WebFluxSleuthOperators;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.SocketUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author alvin
 */
@SpringBootApplication
@RestController
public class SleuthBenchmarkingSpringWebFluxApp implements ApplicationListener<ReactiveWebServerInitializedEvent> {

	static final Scheduler FOO_SCHEDULER = Schedulers.newParallel("foo");

	private static final Logger log = LoggerFactory.getLogger(SleuthBenchmarkingSpringWebFluxApp.class);

	/**
	 * Port to set.
	 */
	public int port;

	public static void main(String... args) {
		new SpringApplicationBuilder(SleuthBenchmarkingSpringWebFluxApp.class).web(WebApplicationType.REACTIVE)
				.application().run(args);
	}

	@RequestMapping("/foo")
	public Mono<String> foo() {
		return Mono.just("foo");
	}

	@Bean
	SkipPatternProvider patternProvider() {
		return () -> Pattern.compile("");
	}

	@Bean
	NettyReactiveWebServerFactory nettyReactiveWebServerFactory(@Value("${server.port:0}") int serverPort) {
		log.info("Starting container at port [" + serverPort + "]");
		return new NettyReactiveWebServerFactory(serverPort == 0 ? SocketUtils.findAvailableTcpPort() : serverPort);
	}

	@Override
	public void onApplicationEvent(ReactiveWebServerInitializedEvent event) {
		this.port = event.getWebServer().getPort();
	}

	@GetMapping("/simple")
	public Mono<String> simple() {
		return Mono.just("hello").map(String::toUpperCase).doOnNext(s -> log.info("Hello from simple [{}]", s));
	}

	// tag::simple_manual[]
	@GetMapping("/simpleManual")
	public Mono<String> simpleManual() {
		return Mono.just("hello").map(String::toUpperCase).doOnEach(WebFluxSleuthOperators
				.withSpanInScope(SignalType.ON_NEXT, signal -> log.info("Hello from simple [{}]", signal.get())));
	}
	// end::simple_manual[]

	@GetMapping("/complexNoSleuth")
	public Mono<String> complexNoSleuth() {
		return Flux.range(1, 10).map(String::valueOf).collect(Collectors.toList())
				.doOnEach(signal -> log.info("Got a request"))
				.flatMap(s -> Mono.delay(Duration.ofMillis(1), FOO_SCHEDULER).map(aLong -> {
					log.info("Logging [{}] from flat map", s);
					return "";
				}));
	}

	@GetMapping("/complex")
	public Mono<String> complex() {
		return Flux.range(1, 10).map(String::valueOf).collect(Collectors.toList())
				.doOnEach(signal -> log.info("Got a request"))
				.flatMap(s -> Mono.delay(Duration.ofMillis(1), FOO_SCHEDULER).map(aLong -> {
					log.info("Logging [{}] from flat map", s);
					return "";
				})).doOnEach(signal -> {
					log.info("Doing assertions");
					TraceContext traceContext = signal.getContext().get(TraceContext.class);
					Assert.notNull(traceContext, "Context must be set by Sleuth instrumentation");
					Assert.state(traceContext.traceId().equals("4883117762eb9420"), "TraceId must be propagated");
					log.info("Assertions passed");
				});
	}

	@GetMapping("/complexManual")
	public Mono<String> complexManual() {
		return Flux.range(1, 10).map(String::valueOf).collect(Collectors.toList())
				.doOnEach(WebFluxSleuthOperators.withSpanInScope(SignalType.ON_NEXT, () -> log.info("Got a request")))
				.flatMap(s -> Mono.subscriberContext().delayElement(Duration.ofMillis(1), FOO_SCHEDULER).map(ctx -> {
					WebFluxSleuthOperators.withSpanInScope(ctx, () -> log.info("Logging [{}] from flat map", s));
					return "";
				})).doOnEach(signal -> {
					WebFluxSleuthOperators.withSpanInScope(signal.getContext(), () -> log.info("Doing assertions"));
					TraceContext traceContext = signal.getContext().get(TraceContext.class);
					Assert.notNull(traceContext, "Context must be set by Sleuth instrumentation");
					Assert.state(traceContext.traceId().equals("4883117762eb9420"), "TraceId must be propagated");
					log.info("Assertions passed");
				});
	}

}
