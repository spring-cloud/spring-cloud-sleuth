package org.springframework.cloud.sleuth.instrument.web.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.sleuth.Trace.PARENT_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;

import java.util.Arrays;
import java.util.List;

import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
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
import org.springframework.cloud.sleuth.TraceContextHolder;
import org.springframework.cloud.sleuth.instrument.web.TraceWebAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { TraceWebAutoConfiguration.class,
		FeignTraceTest.TestConfiguration.class })
@WebIntegrationTest(value = "spring.application.name=fooservice", randomPort = true)
public class FeignTraceTest {

	@Autowired
	TestFeignInterface testFeignInterface;

	@Test
	public void shouldAttachTraceIdWhenUsingFeignClient() {
		// given
		String currentTraceId = "currentTraceId";
		String currentSpanId = "currentSpanId";
		String currentParentId = "currentParentId";
		TraceContextHolder.setCurrentSpan(MilliSpan.builder().traceId(currentTraceId)
				.spanId(currentSpanId).parent(currentParentId).build());

		// when
		ResponseEntity<String> response = testFeignInterface.getTraceId();

		// then
		assertThat(getHeader(response, TRACE_ID_NAME)).isEqualTo(currentTraceId);
		assertThat(getHeader(response, SPAN_ID_NAME)).isEqualTo(currentSpanId);
		assertThat(getHeader(response, PARENT_ID_NAME)).isEqualTo(currentParentId);
	}

	private String getHeader(ResponseEntity<String> response, String name) {
		List<String> headers = response.getHeaders().get(name);
		assertThat(headers).asList().isNotEmpty();
		return headers.get(0);
	}

	@FeignClient("fooservice")
	public interface TestFeignInterface {
		@RequestMapping(method = RequestMethod.GET, value = "/traceid")
		ResponseEntity<String> getTraceId();
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
	}

	@RestController
	public static class FooController {

		@RequestMapping(value = "/traceid", method = RequestMethod.GET)
		public String traceId(@RequestHeader(TRACE_ID_NAME) String traceId,
				@RequestHeader(SPAN_ID_NAME) String spanId,
				@RequestHeader(PARENT_ID_NAME) String parentId) {
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
			balancer.setServersList(Arrays.asList(new Server("localhost", port)));
			return balancer;
		}

	}
}
