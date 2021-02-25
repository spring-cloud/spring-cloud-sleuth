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

package org.springframework.cloud.sleuth.instrument.feign.issues.issue1125;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import feign.Client;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "feign.hystrix.enabled=false" })
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class ManuallyCreatedLoadBalancerFeignClientTests {

	@Autowired
	MyLoadBalancerClient myLoadBalancerClient;

	@Autowired
	AnnotatedFeignClient annotatedFeignClient;

	@Autowired
	TestSpanHandler spans;

	@Before
	public void open() {
		this.spans.clear();
	}

	@Test
	public void should_reuse_custom_feign_client() {
		String response = this.annotatedFeignClient.get();

		then(this.myLoadBalancerClient.wasCalled()).isTrue();
		then(response).isEqualTo("foo");
		// retries
		then(this.spans).hasSize(1);
		then(this.spans.get(0).tags().get("http.path")).isEqualTo("/test");
	}

	@Test
	public void my_client_called() {
		this.annotatedFeignClient.get();
		then(this.myLoadBalancerClient.wasCalled()).isTrue();
	}

	@Test
	public void span_captured() {
		this.annotatedFeignClient.get();
		// retries
		then(this.spans).hasSize(1);
		then(this.spans.get(0).tags().get("http.path")).isEqualTo("/test");
	}

}

@Configuration
@EnableAutoConfiguration
@EnableFeignClients
class Application {

	@Bean
	public Client client(CachingSpringLoadBalancerFactory cachingFactory,
			SpringClientFactory clientFactory) {
		return new MyLoadBalancerClient(new MyDelegateClient(), cachingFactory,
				clientFactory);
	}

	@Bean
	public Sampler defaultSampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

	@Bean
	public SpanHandler testSpanHandler() {
		return new TestSpanHandler();
	}

}

class MyLoadBalancerClient extends LoadBalancerFeignClient {

	MyLoadBalancerClient(Client delegate,
			CachingSpringLoadBalancerFactory lbClientFactory,
			SpringClientFactory clientFactory) {
		super(delegate, lbClientFactory, clientFactory);
	}

	boolean wasCalled;

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		this.wasCalled = true;
		return getDelegate().execute(request, options);
	}

	boolean wasCalled() {
		return this.wasCalled;
	}

}

class MyDelegateClient implements Client {

	boolean wasCalled;

	@Override
	public Response execute(Request request, Request.Options options) {
		this.wasCalled = true;
		return Response.builder().body("foo", StandardCharsets.UTF_8)
				.request(Request.create(Request.HttpMethod.POST, "/foo", new HashMap<>(),
						Request.Body.empty(), new RequestTemplate()))
				.headers(new HashMap<>()).status(200).build();
	}

}

@FeignClient(name = "foo", url = "http://foo")
interface AnnotatedFeignClient {

	@RequestMapping(value = "/test", method = RequestMethod.GET)
	String get();

}
