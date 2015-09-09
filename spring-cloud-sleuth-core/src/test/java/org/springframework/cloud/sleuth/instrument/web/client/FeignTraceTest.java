package org.springframework.cloud.sleuth.instrument.web.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.cloud.sleuth.instrument.web.TraceWebAutoConfiguration;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { TraceWebAutoConfiguration.class,
		FeignTraceTest.TestConfiguration.class })
// TODO: make it work with default isolation
@WebIntegrationTest(value = { "spring.application.name=fooservice",
		"hystrix.command.default.execution.isolation.strategy=SEMAPHORE" }, randomPort = true)
public class FeignTraceTest {

	@Autowired
	TestFeignInterface testFeignInterface;

	@Autowired
	Listener listener;

	@Autowired
	TraceManager traceManager;

	@After
	public void close() {
		TraceContextHolder.removeCurrentTrace();
		this.listener.getEvents().clear();
	}

	@Test
	public void shouldWorkWhenNotTracing() {
		// when
		ResponseEntity<String> response = this.testFeignInterface.getNoTrace();

		// then
		assertThat(getHeader(response, Trace.TRACE_ID_NAME)).isNull();
		assertThat(this.listener.getEvents()).isEmpty();
	}

	@Test
	public void shouldAttachTraceIdWhenUsingFeignClient() {
		// given
		String currentTraceId = "currentTraceId";
		String currentSpanId = "currentSpanId";
		String currentParentId = "currentParentId";
		this.traceManager.continueSpan(MilliSpan.builder().traceId(currentTraceId)
				.spanId(currentSpanId).parent(currentParentId).build());

		// when
		ResponseEntity<String> response = this.testFeignInterface.getTraceId();

		// then
		assertThat(getHeader(response, Trace.TRACE_ID_NAME)).isEqualTo(currentTraceId);
		assertThat(getHeader(response, Trace.SPAN_ID_NAME)).isEqualTo(currentSpanId);
		assertThat(getHeader(response, Trace.PARENT_ID_NAME)).isEqualTo(currentParentId);
		assertThat(this.listener.getEvents().size()).isEqualTo(2);
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
				@RequestHeader(name = Trace.TRACE_ID_NAME, required = false) String traceId) {
			assertThat(traceId).isNull();
			return "OK";
		}

		@RequestMapping(value = "/traceid", method = RequestMethod.GET)
		public String traceId(@RequestHeader(Trace.TRACE_ID_NAME) String traceId,
				@RequestHeader(Trace.SPAN_ID_NAME) String spanId,
				@RequestHeader(Trace.PARENT_ID_NAME) String parentId) {
			assertThat(traceId).isNotEmpty();
			assertThat(parentId).isNotEmpty();
			assertThat(spanId).isNotEmpty();
			return traceId;
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
