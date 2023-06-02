/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.instrument.web.client.reactor.netty;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
		properties = { "spring.sleuth.reactor.netty.debug.enabled=true",
				"spring.sleuth.reactor.instrumentation-type=decorate_queues", "spring.sleuth.sampler.probability=1.0",
				"server.port=8554" })
class LoggingTest {

	private static final String TRACE_ID = "traceId";

	private static final String SPAN_ID = "spanId";

	private static final String ID = "1231231231231231";

	private ListAppender<ILoggingEvent> appender;

	@Autowired
	private TestRestTemplate rest;

	@BeforeEach
	void init() {
		rest.getRestTemplate().setInterceptors(Collections.singletonList((request, body, execution) -> {
			request.getHeaders().add("x-b3-traceid", ID);
			request.getHeaders().add("x-b3-spanid", ID);
			request.getHeaders().add("Foo-Bar-Id", "123");
			return execution.execute(request, body);
		}));
		appender = new ListAppender<>();
		appender.start();
		Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.TRACE);
		((Logger) LoggerFactory.getLogger("reactor.netty.http.client.HttpClientConnect")).addAppender(appender);
		((Logger) LoggerFactory.getLogger(NettyRoutingFilter.class)).addAppender(appender);
	}

	@Test
	void should_properly_fill_out_mdc_context() {
		then(this.rest.getForEntity("http://localhost:8554/headers", String.class).getStatusCode())
				.isEqualTo(HttpStatus.OK);
		then(appender.list).as("No logs to process").isNotEmpty();
		appender.list.forEach(le -> {
			Map<String, String> mdc = le.getMDCPropertyMap();
			then(mdc).as("TraceId does not exist for record: " + le).containsKey(TRACE_ID);
			then(mdc.get(TRACE_ID)).as("TraceId did not match").isEqualTo(ID);
			then(mdc).as("SpanId does not exist for record: " + le).containsKey(SPAN_ID);
			then(mdc).as("SpanId did not match").isEqualTo(SPAN_ID);
		});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class Config {

		MockWebServer server = new MockWebServer();

		@PostConstruct
		void setup() throws IOException {
			server.start();
			server.enqueue(new MockResponse());
		}

		@Bean
		RouteLocator builder(RouteLocatorBuilder builder) {
			return builder.routes().route("test_route",
					r -> r.path("/headers/**").uri("http://localhost:" + server.getPort() + "/foo")).build();
		}

		@PreDestroy
		void cleanup() throws IOException {
			server.close();
		}

	}

}
