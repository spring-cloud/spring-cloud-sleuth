package org.springframework.cloud.sleuth.instrument.web;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.TraceContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {TraceWebAutoConfiguration.class, FeignTraceTest.TestConfiguration.class})
@WebIntegrationTest("server.port:9000")
@ActiveProfiles(FeignTraceTest.FOO_PROFILE)
public class FeignTraceTest {

	public static final String FOO_PROFILE = "foo";

	@Autowired TestFeignInterface testFeignInterface;

	@Test
	public void shouldAttachTraceIdWhenUsingFeignClient() {
		//given
		String currentTraceId = "currentTraceId";
		TraceContextHolder.setCurrentSpan(MilliSpan.builder().traceId(currentTraceId).build());

		//when
		ResponseEntity<String> traceIdEntity = testFeignInterface.getHealth();

		//then
		assertThat(traceIdHeaderFrom(traceIdEntity)).isEqualTo(currentTraceId);
	}

	private String traceIdHeaderFrom(ResponseEntity<String> traceIdEntity) {
		List<String> traceIdHeaders = traceIdEntity.getHeaders().get(TRACE_ID_NAME);
		assertThat(traceIdHeaders).asList().isNotEmpty();
		return traceIdHeaders.get(0);
	}


	@FeignClient("foo-service")
	public interface TestFeignInterface {
		@RequestMapping(method = RequestMethod.GET, value = "/traceid")
		ResponseEntity<String> getHealth();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableFeignClients
	@Profile(FOO_PROFILE)
	public static class TestConfiguration {

		@Bean
		FooController fooController() {
			return new FooController();
		}
	}

	@RestController
	@Profile(FOO_PROFILE)
	public static class FooController {

		@RequestMapping(value = "/traceid", method = RequestMethod.GET)
		public String foo(@RequestHeader(TRACE_ID_NAME) String traceId) {
			assertThat(traceId).isNotEmpty();
			return traceId;
		}
	}
}
