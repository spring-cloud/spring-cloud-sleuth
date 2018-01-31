/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.zuul;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.sampler.Sampler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.BDDAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.discovery.DiscoveryClientRouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SampleZuulProxyApplication.class, properties = {
		"zuul.routes.simple: /simple/**" }, webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class TraceZuulIntegrationTests {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	@Value("${local.server.port}")
	private int port;
	@Autowired
	Tracing tracing;
	@Autowired
	ArrayListSpanReporter spanAccumulator;
	@Autowired
	RestTemplate restTemplate;

	@Before
	@After
	public void cleanup() {
		this.spanAccumulator.clear();
	}

	@Test
	public void should_close_span_when_routing_to_service_via_discovery() {
		Span span = this.tracing.tracer().nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			ResponseEntity<String> result = this.restTemplate.exchange(
					"http://localhost:" + this.port + "/simple/foo", HttpMethod.GET,
					new HttpEntity<>((Void) null), String.class);

			then(result.getStatusCode()).isEqualTo(HttpStatus.OK);
			then(result.getBody()).isEqualTo("Hello world");
		} catch (Exception e) {
			log.error(e);
			throw e;
		} finally {
			span.finish();
		}

		then(this.tracing.tracer().currentSpan()).isNull();
		List<zipkin2.Span> spans = this.spanAccumulator.getSpans();
		then(spans).isNotEmpty();
		everySpanHasTheSameTraceId(spans);
		everyParentIdHasItsCorrespondingSpan(spans);
	}

	@Test
	public void should_close_span_when_routing_to_service_via_discovery_to_a_non_existent_url() {
		Span span = this.tracing.tracer().nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			ResponseEntity<String> result = this.restTemplate.exchange(
					"http://localhost:" + this.port + "/simple/nonExistentUrl",
					HttpMethod.GET, new HttpEntity<>((Void) null), String.class);

			then(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		} finally {
			span.finish();
		}

		then(this.tracing.tracer().currentSpan()).isNull();
		List<zipkin2.Span> spans = this.spanAccumulator.getSpans();
		then(spans).isNotEmpty();
		everySpanHasTheSameTraceId(spans);
		everyParentIdHasItsCorrespondingSpan(spans);
	}

	void everySpanHasTheSameTraceId(List<zipkin2.Span> actual) {
		BDDAssertions.assertThat(actual).isNotNull();
		List<String> traceIds = actual.stream()
				.map(zipkin2.Span::traceId).distinct()
				.collect(toList());
		log.info("Stored traceids " + traceIds);
		assertThat(traceIds).hasSize(1);
	}

	void everyParentIdHasItsCorrespondingSpan(List<zipkin2.Span> actual) {
		BDDAssertions.assertThat(actual).isNotNull();
		List<String> parentSpanIds = actual.stream().map(zipkin2.Span::parentId)
				.filter(Objects::nonNull).collect(toList());
		List<String> spanIds = actual.stream()
				.map(zipkin2.Span::id).distinct()
				.collect(toList());
		List<String> difference = new ArrayList<>(parentSpanIds);
		difference.removeAll(spanIds);
		log.info("Difference between parent ids and span ids " +
				difference.stream().map(span -> "id as hex [" + span + "]").collect(
						joining("\n")));
		assertThat(spanIds).containsAll(parentSpanIds);
	}
}

// Don't use @SpringBootApplication because we don't want to component scan
@Configuration
@EnableAutoConfiguration
@RestController
@EnableZuulProxy
@RibbonClient(name = "simple", configuration = SimpleRibbonClientConfiguration.class)
class SampleZuulProxyApplication {

	@RequestMapping("/foo")
	public String home() {
		return "Hello world";
	}

	@RequestMapping("/exception")
	public String exception() {
		throw new RuntimeException();
	}

	@Bean
	RouteLocator routeLocator(DiscoveryClient discoveryClient,
			ZuulProperties zuulProperties) {
		return new MyRouteLocator("/", discoveryClient, zuulProperties);
	}

	@Bean
	ArrayListSpanReporter testSpanReporter() {
		return new ArrayListSpanReporter();
	}

	@Bean
	RestTemplate restTemplate() {
		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		factory.setReadTimeout(5000);
		RestTemplate restTemplate = new RestTemplate(factory);
		restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
			@Override
			public void handleError(ClientHttpResponse response) throws IOException {

			}
		});
		return restTemplate;
	}

	@Bean
	Sampler alwaysSampler() {
		return Sampler.ALWAYS_SAMPLE;
	}
}

class MyRouteLocator extends DiscoveryClientRouteLocator {

	public MyRouteLocator(String servletPath, DiscoveryClient discovery,
			ZuulProperties properties) {
		super(servletPath, discovery, properties);
	}
}

// Load balancer with fixed server list for "simple" pointing to localhost
@Configuration
class SimpleRibbonClientConfiguration {

	@Value("${local.server.port}")
	private int port;

	@Bean
	public ServerList<Server> ribbonServerList() {
		return new StaticServerList<>(new Server("localhost", this.port));
	}
}
