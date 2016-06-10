package org.springframework.cloud.sleuth.instrument.zuul;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.discovery.DiscoveryClientRouteLocator;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.ListOfSpans;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.zuul.context.RequestContext;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = SampleZuulProxyApplication.class)
@WebAppConfiguration
@IntegrationTest({ "server.port: 0", "zuul.routes.simple: /simple/**" })
@DirtiesContext
public class TraceZuulIntegrationTests {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	@Value("${local.server.port}")
	private int port;
	@Autowired Tracer tracer;
	@Autowired ArrayListSpanAccumulator spanAccumulator;
	@Autowired RestTemplate restTemplate;

	@Before
	public void cleanup() {
		TestSpanContextHolder.removeCurrentSpan();
		RequestContext.getCurrentContext().unset();
		this.spanAccumulator.getSpans().clear();
	}

	@Test
	public void should_close_span_when_routing_to_service_via_discovery() {
		Span span = this.tracer.createSpan("new_span");
		log.info("Started span " + span);
		ResponseEntity<String> result = this.restTemplate.exchange(
				"http://localhost:" + this.port + "/simple/foo", HttpMethod.GET,
				new HttpEntity<>((Void) null), String.class);

		this.tracer.close(span);

		then(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		then(result.getBody()).isEqualTo("Hello world");
		then(this.tracer.getCurrentSpan()).isNull();
		then(new ListOfSpans(this.spanAccumulator.getSpans()))
				.everyParentIdHasItsCorrespondingSpan()
				.clientSideSpanWithNameHasTags("http:/simple/foo", TestTag.tag()
						.tag("http.method", "GET")
						.tag("http.status_code", "200")
						.tag("http.path", "/simple/foo"));
	}

	@Test
	public void should_close_span_when_routing_to_service_via_discovery_to_a_non_existent_url() {
		Span span = this.tracer.createSpan("new_span");
		log.info("Started span " + span);
		ResponseEntity<String> result = this.restTemplate.exchange(
				"http://localhost:" + this.port + "/simple/nonExistentUrl", HttpMethod.GET,
				new HttpEntity<>((Void) null), String.class);

		this.tracer.close(span);

		then(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		then(this.tracer.getCurrentSpan()).isNull();
		then(new ListOfSpans(this.spanAccumulator.getSpans()))
				.everyParentIdHasItsCorrespondingSpan()
				.clientSideSpanWithNameHasTags("http:/simple/nonExistentUrl", TestTag.tag()
						.tag("http.method", "GET")
						.tag("http.status_code", "404")
						.tag("http.path", "/simple/nonExistentUrl"));
	}

	private static class TestTag extends HashMap<String, String> {

		public static TestTag tag() {
			return new TestTag();
		}

		public TestTag tag(String key, String value) {
			put(key, value);
			return this;
		}
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

	@Bean RouteLocator routeLocator(DiscoveryClient discoveryClient, ZuulProperties zuulProperties) {
		return new MyRouteLocator("/", discoveryClient, zuulProperties);
	}

	@Bean SpanReporter testSpanReporter() {
		return new ArrayListSpanAccumulator();
	}

	@Bean RestTemplate restTemplate() {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
			@Override public void handleError(ClientHttpResponse response)
					throws IOException {

			}
		});
		return restTemplate;
	}

	@Bean Sampler alwaysSampler() {
		return new AlwaysSampler();
	}
}

class MyRouteLocator extends DiscoveryClientRouteLocator {

	public MyRouteLocator(String servletPath, DiscoveryClient discovery, ZuulProperties properties) {
		super(servletPath, discovery, properties);
	}
}

// Load balancer with fixed server list for "simple" pointing to localhost
@Configuration
class SimpleRibbonClientConfiguration {

	@Value("${local.server.port}") private int port;

	@Bean public ServerList<Server> ribbonServerList() {
		return new StaticServerList<>(new Server("localhost", this.port));
	}
}
