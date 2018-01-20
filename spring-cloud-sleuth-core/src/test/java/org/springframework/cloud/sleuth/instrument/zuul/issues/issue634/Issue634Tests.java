package org.springframework.cloud.sleuth.instrument.zuul.issues.issue634;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.zuul.ZuulFilter;

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
	@Autowired HttpTracing tracer;
	@Autowired TraceCheckingSpanFilter filter;
	@Autowired ArrayListSpanReporter reporter;

	@Test
	public void should_reuse_custom_feign_client() {
		for (int i = 0; i < 15; i++) {
			new TestRestTemplate()
					.getForEntity("http://localhost:" + this.port + "/display/ddd",
							String.class);

			then(this.tracer.tracing().tracer().currentSpan()).isNull();
		}

		then(new HashSet<>(this.filter.counter.values()))
				.describedAs("trace id should not be reused from thread").hasSize(1);
		then(this.reporter.getSpans()).isNotEmpty();
	}
}

@EnableZuulProxy
@EnableAutoConfiguration
@Configuration
class TestZuulApplication {

	@Bean TraceCheckingSpanFilter traceCheckingSpanFilter(Tracing tracer) {
		return new TraceCheckingSpanFilter(tracer);
	}

	@Bean Sampler sampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

	@Bean ArrayListSpanReporter reporter() {
		return new ArrayListSpanReporter();
	}

}

class TraceCheckingSpanFilter extends ZuulFilter {

	private final Tracing tracer;
	final Map<Long, Integer> counter = new ConcurrentHashMap<>();

	TraceCheckingSpanFilter(Tracing tracer) {
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
		long trace = this.tracer.tracer().currentSpan().context().traceId();
		Integer integer = this.counter.getOrDefault(trace, 0);
		counter.put(trace, integer + 1);
		return null;
	}
}