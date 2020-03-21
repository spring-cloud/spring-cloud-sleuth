/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import brave.http.HttpTracing;
import brave.test.http.ITHttpServer;
import org.junit.After;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.cloud.sleuth.instrument.web.client.TraceWebClientAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * This runs Brave's integration tests, ensuring common instrumentation bugs aren't
 * present.
 */
public class WebFluxBraveTests extends ITHttpServer {

	AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();

	ConfigurableApplicationContext context;

	volatile int port;

	@Override
	protected void init() {
		stop();

		// Not sure if there's a way to programmatically add a bean to SpringApplicationBuilder
		parent = new AnnotationConfigApplicationContext();
		parent.registerBean(HttpTracing.class, () -> httpTracing);
		parent.refresh();

		context = new SpringApplicationBuilder(Config.class).parent(parent)
				.web(WebApplicationType.REACTIVE).properties("server.port=0").run();
		port = context.getBean(Environment.class)
				.getProperty("local.server.port", Integer.class);
	}

	@Override
	protected String url(String path) {
		return "http://127.0.0.1:" + port + path;
	}

	@After
	public void stop() {
		if (context != null) {
			context.close();
		}
		if (parent != null) {
			parent.close();
		}
	}

	@Configuration
	@Import(WebFluxController.class)
	// TODO: become more specific, like which autoconfiguration are needed
	@EnableAutoConfiguration(exclude = TraceWebClientAutoConfiguration.class)
	static class Config {

		@Bean
		ReactiveWebServerFactory reactiveWebServerFactory() {
			return new NettyReactiveWebServerFactory();
		}

	}

}
