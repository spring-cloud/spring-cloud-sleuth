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

package org.springframework.cloud.sleuth.instrument.feign.issues.issueX;

import static org.assertj.core.api.BDDAssertions.then;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;

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
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import brave.Tracing;
import brave.sampler.Sampler;
import feign.Client;
import feign.Request;
import feign.Response;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

@FeignClient(name = "foo", url = "https://non.existing.url")
interface MyNameRemote {

	@RequestMapping(value = "/", method = RequestMethod.GET)
	String get();

}

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "feign.hystrix.enabled=false" })
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class IssueXTests {

	@Autowired
	MyClient myClient;

	@Autowired
	MyNameRemote myNameRemote;

	@Autowired
	ArrayListSpanReporter reporter;

	@Autowired
	Tracing tracer;

	@Before
	public void open() {
		this.reporter.clear();
	}

	@Test // Failing
	public void should_reuse_custom_feign_client() {
		System.out.println("reuse test called");
		String response = this.myNameRemote.get();

		then(this.myClient.wasCalled()).isTrue();
		then(response).isEqualTo("foo");
		List<Span> spans = this.reporter.getSpans();
		// retries
		then(spans).hasSize(1);
		then(spans.get(0).tags().get("http.path")).isEqualTo("/");
	}
	
	@Test // Failing
	public void my_client_called() {
		System.out.println("client called test");
		this.myNameRemote.get();
		then(this.myClient.wasCalled()).isTrue();
	}
	
	@Test // Passing
	public void span_captured() {
		System.out.println("Span captured test");
		this.myNameRemote.get();
		List<Span> spans = this.reporter.getSpans();
		// retries
		then(spans).hasSize(1);
		then(spans.get(0).tags().get("http.path")).isEqualTo("/");
	}

}

@Configuration
@EnableAutoConfiguration
@EnableFeignClients
class Application {

	@Bean
	public Client client(CachingSpringLoadBalancerFactory cachingFactory, SpringClientFactory clientFactory) {
		return new MyClient(new MyDelegateClient(), cachingFactory, clientFactory);
	}

	@Bean
	public Sampler defaultSampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

	@Bean
	public Reporter<Span> spanReporter() {
		return new ArrayListSpanReporter();
	}

}

class MyClient extends LoadBalancerFeignClient {

	public MyClient(Client delegate, CachingSpringLoadBalancerFactory lbClientFactory,
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
	public Response execute(Request request, Request.Options options) throws IOException {
		this.wasCalled = true;
		return Response.builder().body("foo", Charset.forName("UTF-8"))
				.request(Request.create(Request.HttpMethod.POST, "/foo", new HashMap<>(),
						Request.Body.empty()))
				.headers(new HashMap<>()).status(200).build();
	}

	boolean wasCalled() {
		return this.wasCalled;
	}
}
