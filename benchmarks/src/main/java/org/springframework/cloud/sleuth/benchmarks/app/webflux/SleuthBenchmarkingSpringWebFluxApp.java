/*
 * Copyright 2013-2018 the original author or authors.
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

import brave.Tracer;
import brave.sampler.Sampler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.context.ReactiveWebServerInitializedEvent;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.util.SocketUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import java.util.regex.Pattern;


/**
 * @author alvin
 */
@SpringBootApplication
@RestController
public class SleuthBenchmarkingSpringWebFluxApp implements ApplicationListener<ReactiveWebServerInitializedEvent> {

	private static final Log log = LogFactory.getLog(SleuthBenchmarkingSpringWebFluxApp.class);

	public int port;

	@RequestMapping("/foo")
	public Mono<String> foo() {
		return Mono.just("foo");
	}

	@Bean
	Sampler alwaysSampler() {
		return Sampler.ALWAYS_SAMPLE;
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

	public static void main(String... args) {
		new SpringApplicationBuilder(SleuthBenchmarkingSpringWebFluxApp.class)
				.web(WebApplicationType.REACTIVE)
				.application()
				.run(args);
	}

	@Bean
	public Reporter<Span> reporter() {
		return Reporter.NOOP;
	}



	@Override
	public void onApplicationEvent(ReactiveWebServerInitializedEvent event) {
		this.port = event.getWebServer().getPort();
	}
}

