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

package org.springframework.cloud.sleuth.instrument.feign.issues.issueY;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;

import brave.Tracing;
import brave.sampler.Sampler;
import feign.Client;
import feign.Contract;
import feign.Feign;
import feign.Request;
import feign.Response;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.cloud.openfeign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.assertj.core.api.BDDAssertions.then;

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
public class IssueYTests {

	@Autowired
	MyClient myClient;
	
	@Autowired
	MyDelegateClient myDelegateClient;

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

	@Test
	public void should_reuse_custom_feign_client() {
		String response = this.myNameRemote.get();

		then(this.myClient.wasCalled()).isTrue();
		then(this.myDelegateClient.wasCalled()).isTrue();
		then(response).isEqualTo("foo");
		System.out.println("this.myclient.wascalled: " + this.myClient.wasCalled());
		List<Span> spans = this.reporter.getSpans();
		// retries
		then(spans).hasSize(1);
		then(spans.get(0).tags().get("http.path")).isEqualTo("/");
	}
	
	@Test
	public void my_client_called() {
		this.myNameRemote.get();
		then(this.myClient.wasCalled()).isTrue();
		then(this.myDelegateClient.wasCalled()).isTrue();
	}
	
	@Test
	public void span_captured() {
		this.myNameRemote.get();
		List<Span> spans = this.reporter.getSpans();
		// retries
		then(spans).hasSize(1);
		then(spans.get(0).tags().get("http.path")).isEqualTo("/");
	}

}

@Configuration
@EnableAutoConfiguration
@Import(FeignClientsConfiguration.class)
class Application {

	@Bean
	public Client myDelegateClient() {
		return new MyDelegateClient();
	}
	
	@Bean
	public Client client(MyDelegateClient myDelegateClient, CachingSpringLoadBalancerFactory cachingFactory, SpringClientFactory clientFactory) {
		return new MyClient(myDelegateClient, cachingFactory, clientFactory);
	}

	@Bean
	public MyNameRemote myNameRemote(Client client, Decoder decoder, Encoder encoder, Contract contract) {
		return Feign.builder().client(client)
				.encoder(encoder)
				.decoder(decoder)
				.contract(contract)
				.target(new HardCodedTarget<MyNameRemote>(MyNameRemote.class, "foo", "https://non.existing.url"));
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
