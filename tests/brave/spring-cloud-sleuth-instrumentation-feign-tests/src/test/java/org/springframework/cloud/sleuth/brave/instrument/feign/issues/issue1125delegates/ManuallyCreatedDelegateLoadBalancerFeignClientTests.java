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

package org.springframework.cloud.sleuth.brave.instrument.feign.issues.issue1125delegates;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import brave.handler.SpanHandler;
import brave.http.HttpRequest;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.test.TestSpanHandler;
import feign.Client;
import feign.Contract;
import feign.Feign;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.cloud.sleuth.instrument.web.HttpClientSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class ManuallyCreatedDelegateLoadBalancerFeignClientTests {

	@Autowired
	MyDelegateClient myDelegateClient;

	@Autowired
	AnnotatedFeignClient annotatedFeignClient;

	@Autowired
	TestSpanHandler spans;

	@Autowired
	MyDelegateClient myClient;

	@BeforeEach
	public void open() {
		this.spans.clear();
	}

	@Test
	public void should_reuse_custom_feign_client() {
		String response = this.annotatedFeignClient.get();

		// then(this.myClient.wasCalled()).isTrue();
		then(this.myDelegateClient.wasCalled()).isTrue();
		then(response).isEqualTo("foo");
		// retries
		then(this.spans).hasSize(1);
		then(this.spans.get(0).tags().get("http.path")).isEqualTo("/test");
	}

	@Test
	public void my_client_called() {
		this.annotatedFeignClient.get();
		then(this.myClient.wasCalled()).isTrue();
		then(this.myDelegateClient.wasCalled()).isTrue();
	}

	@Test
	public void span_captured() {
		this.annotatedFeignClient.get();
		// retries
		then(this.spans).hasSize(1);
		then(this.spans.get(0).tags().get("http.path")).isEqualTo("/test");
	}

}

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(FeignClientsConfiguration.class)
class Application {

	@Bean
	public Client myDelegateClient() {
		return new MyDelegateClient();
	}

	@Bean
	public AnnotatedFeignClient annotatedFeignClient(Client client, Decoder decoder, Encoder encoder,
			Contract contract) {
		return Feign.builder().client(client).encoder(encoder).decoder(decoder).contract(contract)
				.target(new HardCodedTarget<>(AnnotatedFeignClient.class, "foo", "http://foo"));
	}

	@Bean
	public Sampler defaultSampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

	@Bean
	public SpanHandler testSpanHandler() {
		return new TestSpanHandler();
	}

	@Bean(name = HttpClientSampler.NAME)
	@HttpClientSampler
	public SamplerFunction<HttpRequest> clientHttpSampler() {
		return arg -> true;
	}

}

class MyDelegateClient implements Client {

	boolean wasCalled;

	@Override
	public Response execute(Request request, Request.Options options) {
		this.wasCalled = true;
		return Response
				.builder().body("foo", StandardCharsets.UTF_8).request(Request.create(Request.HttpMethod.POST, "/foo",
						new HashMap<>(), Request.Body.empty(), new RequestTemplate()))
				.headers(new HashMap<>()).status(200).build();
	}

	boolean wasCalled() {
		return this.wasCalled;
	}

}

@FeignClient(name = "foo", url = "http://foo")
interface AnnotatedFeignClient {

	@RequestMapping(value = "/test", method = RequestMethod.GET)
	String get();

}
