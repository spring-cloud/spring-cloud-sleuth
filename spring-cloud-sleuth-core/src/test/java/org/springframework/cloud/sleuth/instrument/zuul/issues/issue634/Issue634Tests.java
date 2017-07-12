package org.springframework.cloud.sleuth.instrument.zuul.issues.issue634;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.zuul.ZuulFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestZuulApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"feign.hystrix.enabled=false",
				"zuul.routes.dp.path:/display/**",
				"zuul.routes.dp.path.url: http://localhost:9987/unknown"})
@DirtiesContext
public class Issue634Tests {

	@LocalServerPort int port;
	@Autowired Tracer tracer;
	@Autowired TraceCheckingSpanFilter filter;

	@Before
	public void setup() {
		TestSpanContextHolder.removeCurrentSpan();
		ExceptionUtils.setFail(true);
	}

	@After
	public void close() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_reuse_custom_feign_client() {
		for (int i = 0; i < 15; i++) {
			new TestRestTemplate()
					.getForEntity("http://localhost:" + this.port + "/display/ddd",
							String.class);

			then(this.tracer.getCurrentSpan()).isNull();
			then(ExceptionUtils.getLastException()).isNull();
		}
		then(new HashSet<>(this.filter.counter.values()))
				.describedAs("trace id should not be reused from thread").hasSize(1);
	}
}

@EnableZuulProxy
@EnableAutoConfiguration
@Configuration
class TestZuulApplication {

	@Bean
	TraceCheckingSpanFilter traceCheckingSpanFilter(Tracer tracer) {
		return new TraceCheckingSpanFilter(tracer);
	}

}

class TraceCheckingSpanFilter extends ZuulFilter {

	private final Tracer tracer;
	final Map<Long, Integer> counter = new ConcurrentHashMap<>();

	TraceCheckingSpanFilter(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override public String filterType() {
		return "post";
	}

	@Override public int filterOrder() {
		return -1;
	}

	@Override public boolean shouldFilter() {
		return true;
	}

	@Override public Object run() {
		long trace = this.tracer.getCurrentSpan().getTraceId();
		Integer integer = this.counter.getOrDefault(trace, 0);
		counter.put(trace, integer + 1);
		return null;
	}
}