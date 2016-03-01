package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { FeignTraceTests.TestConfiguration.class })
@WebIntegrationTest(value = { "spring.application.name=fooservice" }, randomPort = true)
@DirtiesContext
public class FeignTraceTests {

	@Autowired
	TestFeignInterface testFeignInterface;

	@Autowired
	Listener listener;

	@Autowired
	Tracer tracer;

	@After
	public void close() {
		TestSpanContextHolder.removeCurrentSpan();
		this.listener.getEvents().clear();
	}

	@Test
	public void shouldCreateANewSpanWhenNoPreviousTracingWasPresent() {
		ResponseEntity<String> response = this.testFeignInterface.getNoTrace();

		then(getHeader(response, Span.TRACE_ID_NAME)).isNotNull();
		then(this.listener.getEvents()).isNotEmpty();
	}

	@Test
	public void shouldPropagateNotSamplingHeader() {
		Long currentTraceId = 1L;
		Long currentParentId = 2L;
		this.tracer.continueSpan(Span.builder().traceId(currentTraceId)
				.spanId(generatedId()).exportable(false).parent(currentParentId).build());

		ResponseEntity<Map<String, String>> response = this.testFeignInterface.headers();

		then(response.getBody().get(Span.TRACE_ID_NAME)).isNotNull();
		then(response.getBody().get(Span.NOT_SAMPLED_NAME)).isNotNull();
		then(this.listener.getEvents()).isNotEmpty();
	}

	@Test
	public void shouldAttachTraceIdWhenUsingFeignClient() {
		Long currentTraceId = 1L;
		Long currentParentId = 2L;
		Long currentSpanId = generatedId();
		this.tracer.continueSpan(Span.builder().traceId(currentTraceId)
				.spanId(currentSpanId).parent(currentParentId).build());

		ResponseEntity<String> response = this.testFeignInterface.getTraceId();

		then(Span.hexToId(getHeader(response, Span.TRACE_ID_NAME)))
				.isEqualTo(currentTraceId);
		then(Span.hexToId(getHeader(response, Span.PARENT_ID_NAME)))
				.isEqualTo(currentSpanId);
		then(this.listener.getEvents().size()).isEqualTo(2);
	}

	private Long generatedId() {
		return new Random().nextLong();
	}

	private String getHeader(ResponseEntity<String> response, String name) {
		List<String> headers = response.getHeaders().get(name);
		return headers == null || headers.isEmpty() ? null : headers.get(0);
	}

	@FeignClient("fooservice")
	public interface TestFeignInterface {
		@RequestMapping(method = RequestMethod.GET, value = "/traceid")
		ResponseEntity<String> getTraceId();

		@RequestMapping(method = RequestMethod.GET, value = "/notrace")
		ResponseEntity<String> getNoTrace();

		@RequestMapping(method = RequestMethod.GET, value = "/")
		ResponseEntity<Map<String, String>> headers();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableFeignClients
	@RibbonClient(name = "fooservice", configuration = SimpleRibbonClientConfiguration.class)
	public static class TestConfiguration {

		@Bean
		FooController fooController() {
			return new FooController();
		}

		@Bean
		Listener listener() {
			return new Listener();
		}
	}

	@Component
	public static class Listener {
		private List<ApplicationEvent> events = new ArrayList<>();

		@EventListener(ClientSentEvent.class)
		public void sent(ClientSentEvent event) {
			this.events.add(event);
		}

		@EventListener(ClientReceivedEvent.class)
		public void received(ClientReceivedEvent event) {
			this.events.add(event);
		}

		public List<ApplicationEvent> getEvents() {
			return this.events;
		}
	}

	@RestController
	public static class FooController {

		@RequestMapping(value = "/notrace", method = RequestMethod.GET)
		public String notrace(
				@RequestHeader(name = Span.TRACE_ID_NAME, required = false) String traceId) {
			then(traceId).isNotNull();
			return "OK";
		}

		@RequestMapping(value = "/traceid", method = RequestMethod.GET)
		public String traceId(@RequestHeader(Span.TRACE_ID_NAME) String traceId,
				@RequestHeader(Span.SPAN_ID_NAME) String spanId,
				@RequestHeader(Span.PARENT_ID_NAME) String parentId) {
			then(traceId).isNotEmpty();
			then(parentId).isNotEmpty();
			then(spanId).isNotEmpty();
			return traceId;
		}

		@RequestMapping("/")
		public Map<String, String> home(@RequestHeader HttpHeaders headers) {
			Map<String, String> map = new HashMap<String, String>();
			for (String key : headers.keySet()) {
				map.put(key, headers.getFirst(key));
			}
			return map;
		}

	}

	@Configuration
	public static class SimpleRibbonClientConfiguration {

		@Value("${local.server.port}")
		private int port = 0;

		@Bean
		public ILoadBalancer ribbonLoadBalancer() {
			BaseLoadBalancer balancer = new BaseLoadBalancer();
			balancer.setServersList(Arrays.asList(new Server("localhost", this.port)));
			return balancer;
		}

	}
}
