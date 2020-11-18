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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpRequestParser;
import org.springframework.cloud.sleuth.api.http.HttpResponseParser;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

@ContextConfiguration(classes = HttpServerParserTests.TestConfiguration.class)
public abstract class HttpServerParserTests {

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracer tracer;

	@LocalServerPort
	int port;

	@Autowired
	FooController fooController;

	@AfterEach
	@BeforeEach
	public void close() {
		this.spans.clear();
		this.fooController.clear();
	}

	@Test
	public void should_set_tags_via_server_parsers() {
		BDDAssertions.then(new RestTemplate().getForObject("http://localhost:" + this.port + "/hello", String.class))
				.isEqualTo("hello");

		Awaitility.await()
				.untilAsserted(() -> then(serverSideTags()).containsEntry("ServerRequest", "Tag")
						.containsEntry("ServerRequestServlet", "GET").containsEntry("ServerResponse", "Tag")
						.containsEntry("ServerResponseServlet", "200"));
	}

	protected Map<String, String> serverSideTags() {
		return spans.reportedSpans().stream().filter(f -> f.getKind().equals(Span.Kind.SERVER))
				.flatMap(f -> f.getTags().entrySet().stream())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = JmxAutoConfiguration.class)
	@Import(ServerParserConfiguration.class)
	public static class TestConfiguration {

		@Bean
		FooController fooController() {
			return new FooController();
		}

	}

	@RestController
	public static class FooController {

		Span span;

		@RequestMapping(value = "/hello", method = RequestMethod.GET)
		public String hello() {
			return "hello";
		}

		public Span getSpan() {
			return this.span;
		}

		public void clear() {
			this.span = null;
		}

	}

	// tag::server_parser_config[]
	@Configuration(proxyBeanMethods = false)
	public static class ServerParserConfiguration {

		@Bean(name = HttpServerRequestParser.NAME)
		HttpRequestParser myHttpRequestParser() {
			return (request, context, span) -> {
				// Span customization
				span.tag("ServerRequest", "Tag");
				Object unwrap = request.unwrap();
				if (unwrap instanceof HttpServletRequest) {
					HttpServletRequest req = (HttpServletRequest) unwrap;
					// Span customization
					span.tag("ServerRequestServlet", req.getMethod());
				}
			};
		}

		@Bean(name = HttpServerResponseParser.NAME)
		HttpResponseParser myHttpResponseParser() {
			return (response, context, span) -> {
				// Span customization
				span.tag("ServerResponse", "Tag");
				Object unwrap = response.unwrap();
				if (unwrap instanceof HttpServletResponse) {
					HttpServletResponse resp = (HttpServletResponse) unwrap;
					// Span customization
					span.tag("ServerResponseServlet", String.valueOf(resp.getStatus()));
				}
			};
		}

	}
	// end::server_parser_config[]

}
