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

package org.springframework.cloud.sleuth.instrument.feign.issues.issue502;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import brave.Tracing;
import brave.http.HttpRequest;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import feign.Client;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.sleuth.instrument.web.HttpClientSampler;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.assertj.core.api.BDDAssertions.then;

@FeignClient(name = "foo", url = "https://non.existing.url")
interface MyNameRemote {

	@RequestMapping(value = "/", method = RequestMethod.GET)
	String get();

}

/**
 * @author Marcin Grzejszczak
 */
@SpringBootTest(classes = Application.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class Issue502Tests {

	@Autowired
	MyClient myClient;

	@Autowired
	MyNameRemote myNameRemote;

	@Autowired
	ArrayListSpanReporter reporter;

	@Autowired
	Tracing tracer;

	@BeforeEach
	public void open() {
		this.reporter.clear();
	}

	@Test
	public void should_reuse_custom_feign_client() {
		String response = this.myNameRemote.get();

		then(this.myClient.wasCalled()).isTrue();
		then(response).isEqualTo("foo");
		List<Span> spans = this.reporter.getSpans();
		// retries
		then(spans).hasSize(1);
		then(spans.get(0).tags().get("http.path")).isEqualTo("");
	}

}

@Configuration
@EnableAutoConfiguration
@EnableFeignClients
class Application {

	@Bean
	public Client client() {
		return new MyClient();
	}

	@Bean
	public Sampler defaultSampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

	@Bean
	public Reporter<Span> spanReporter() {
		return new ArrayListSpanReporter();
	}

	@Bean(name = HttpClientSampler.NAME)
	@HttpClientSampler
	public SamplerFunction<HttpRequest> clientHttpSampler() {
		return arg -> true;
	}

}

class MyClient implements Client {

	boolean wasCalled;

	@Override
	public Response execute(Request request, Request.Options options) {
		this.wasCalled = true;
		return Response.builder().body("foo", StandardCharsets.UTF_8)
				.request(Request.create(Request.HttpMethod.POST, "/foo", new HashMap<>(),
						Request.Body.empty(), new RequestTemplate()))
				.headers(new HashMap<>()).status(200).build();
	}

	boolean wasCalled() {
		return this.wasCalled;
	}

}
