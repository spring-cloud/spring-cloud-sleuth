/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.wavefront;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import brave.Tracing;
import brave.internal.Platform;
import brave.opentracing.BraveTracer;
import brave.sampler.Sampler;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for tracing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = WavefrontTracingIntegrationTests.Config.class,
		properties = { "wavefront.application.name=IntegratedTracingTests", "spring.application.name=test_service" })
@AutoConfigureWebTestClient
@AutoConfigureMetrics
@DirtiesContext
public class WavefrontTracingIntegrationTests {

	@Autowired
	private WebTestClient client;

	@Autowired
	private BlockingDeque<SpanRecord> spanRecordQueue;

	@Test
	void sendsToWavefront() {
		this.client.get().uri("/api/fn/10").header("b3", "0000000000000001-0000000000000003-1-0000000000000002")
				.exchange().expectStatus().isOk();

		SpanRecord spanRecord = takeRecord(spanRecordQueue);
		assertThat(spanRecord.traceId).hasToString("00000000-0000-0000-0000-000000000001");
		assertThat(spanRecord.parents).extracting(UUID::toString)
				.containsExactly("00000000-0000-0000-0000-000000000003");
		assertThat(spanRecord.followsFrom).isNull();
		// This tests that RPC spans do not share the same span ID
		assertThat(spanRecord.spanId.toString()).isNotEqualTo("00000000-0000-0000-0000-000000000003")
				.matches("^[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}$");
		assertThat(spanRecord.name).isEqualTo("GET /api/fn/{id}");

		// spot check the unit is valid (millis not micros)
		long currentTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
		assertThat(spanRecord.startMillis).isGreaterThan(currentTime - 5000).isLessThan(currentTime);
		// Less than a millis should round up to 1, but the test could take longer than
		// 1ms
		assertThat(spanRecord.durationMillis).isPositive();

		// http
		assertThat(spanRecord.tags).containsExactlyInAnyOrder(Pair.of("application", "IntegratedTracingTests"),
				Pair.of("service", "test_service"), Pair.of("cluster", "none"), Pair.of("shard", "none"),
				Pair.of("http.method", "GET"), Pair.of("http.path", "/api/fn/10"),
				Pair.of("mvc.controller.class", "WebMvcController"), Pair.of("mvc.controller.method", "fn"),
				Pair.of("span.kind", "server"), Pair.of("ipv4", Platform.get().linkLocalIp()));
	}

	@Test
	void http_badRequest_setsStatusCodeAndErrorTrueTags() {
		this.client.get().uri("/badrequest").exchange().expectStatus().isBadRequest();

		SpanRecord spanRecord = takeRecord(spanRecordQueue);

		// http
		assertThat(spanRecord.tags).contains(Pair.of("http.status_code", "400"), Pair.of("error", "true"));
	}

	@Test
	void setsStatusCodeAndErrorTrueTags_opentracing() {
		this.client.get().uri("/error/opentracing").exchange().expectStatus().is5xxServerError();

		SpanRecord spanRecord = takeRecord(spanRecordQueue);
		// http
		assertThat(spanRecord.tags).contains(Pair.of("http.status_code", "500"), Pair.of("error", "true")); // retains
																											// the
																											// boolean
																											// true
	}

	@Test
	void setsStatusCodeAndErrorTrueTags_brave() {
		this.client.get().uri("/error/brave").exchange().expectStatus().is5xxServerError();

		SpanRecord spanRecord = takeRecord(spanRecordQueue);
		// http
		assertThat(spanRecord.tags).contains(Pair.of("http.status_code", "500"), Pair.of("error", "true")); // deletes
																											// the
																											// user
																											// message
	}

	@Test
	void setsStatusCodeAndErrorTrueTags_exception() {
		this.client.get().uri("/error/exception").exchange().expectStatus().is5xxServerError();

		// http
		assertThat(takeRecord(spanRecordQueue).tags).contains(Pair.of("http.status_code", "500"),
				Pair.of("error", "true") // deletes the exception message
		);
	}

	@Test
	void setsStatusCodeAndErrorTrueTags_thrownException() {
		this.client.get().uri("/throws").exchange().expectStatus().is5xxServerError();

		SpanRecord spanRecord = takeRecord(spanRecordQueue);
		// http
		assertThat(spanRecord.tags).contains(
				/* Pair.of("http.status_code", "500"), */ // Able to return error=true
															// span tag but not
															// http.status_code span tag
				Pair.of("error", "true") // deletes the exception message
		);
	}

	/** Helps ensure test bugs don't result in hung tests! */
	<R> R takeRecord(BlockingDeque<R> queue) {
		R result;
		try {
			result = queue.poll(3, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}

		assertThat(result).withFailMessage("Record was not reported").isNotNull();
		return result;
	}

	/** Makes sure tests aren't accidentally not verifying all reported data. */
	@AfterEach
	void ensureNoExtraSpans() {
		try {
			SpanRecord span = spanRecordQueue.poll(100, TimeUnit.MILLISECONDS);
			assertThat(span).withFailMessage("Span remaining in queue. Check for redundant reporting: %s", span)
					.isNull();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config {

		/**
		 * This uses a {@linkplain Controller WebMVC controller} as it is the most popular
		 * way to write Spring services and has no instrumentation gotchas or scope bugs
		 * like reactive tracing. This allows us to focus on api and data mapping issues,
		 * which is the heart of this test.
		 */
		@Bean
		WebMvcController controller() {
			return new WebMvcController();
		}

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		/**
		 * Sleuth would automatically wire this, except there's another impl in the
		 * classpath.
		 */
		@Bean
		Tracer opentracing(Tracing tracing) {
			return BraveTracer.create(tracing);
		}

		@Bean
		BlockingDeque<SpanRecord> spanRecordQueue() {
			return new LinkedBlockingDeque<>();
		}

		@Bean
		@Primary
		WavefrontSender wavefrontSender(BlockingDeque<SpanRecord> spanRecordQueue) {
			return new WavefrontSender() {
				@Override
				public String getClientId() {
					return null;
				}

				@Override
				public void flush() {

				}

				@Override
				public int getFailureCount() {
					return 0;
				}

				@Override
				public void sendDistribution(String name, List<Pair<Double, Integer>> centroids,
						Set<HistogramGranularity> histogramGranularities, Long timestamp, String source,
						Map<String, String> tags) {

				}

				@Override
				public void sendMetric(String name, double value, Long timestamp, String source,
						Map<String, String> tags) {

				}

				@Override
				public void sendFormattedMetric(String point) {

				}

				@Override
				public void sendSpan(String name, long startMillis, long durationMillis, String source, UUID traceId,
						UUID spanId, List<UUID> parents, List<UUID> followsFrom, List<Pair<String, String>> tags,
						List<SpanLog> spanLogs) {
					spanRecordQueue.add(new SpanRecord(name, startMillis, durationMillis, source, traceId, spanId,
							parents, followsFrom, tags, spanLogs));
				}

				@Override
				public void close() {

				}
			};
		}

		@Bean
		ApplicationTags applicationTags(Environment environment) {
			return createFromProperties(environment);
		}

		public ApplicationTags createFromProperties(Environment environment) {
			String application = environment.getProperty("wavefront.application.name");
			String service = environment.getProperty("wavefront.application.service");
			service = (StringUtils.hasText(service)) ? service : defaultServiceName(environment);
			ApplicationTags.Builder builder = new ApplicationTags.Builder(application, service);
			builder.cluster(environment.getProperty("wavefront.application.cluster"));
			builder.shard(environment.getProperty("wavefront.application.shard"));
			return builder.build();
		}

		private String defaultServiceName(Environment environment) {
			String applicationName = environment.getProperty("spring.application.name");
			return (StringUtils.hasText(applicationName)) ? applicationName : "unnamed_service";
		}

	}

	@Controller
	static class WebMvcController {

		@Autowired
		Tracer opentracing;

		@Autowired
		brave.Tracer tracer;

		@RequestMapping("/api/fn/{id}")
		public ResponseEntity<String> fn(@PathVariable("id") String id) {
			return new ResponseEntity<>(id, HttpStatus.OK);
		}

		@RequestMapping("/error/{api}")
		public ResponseEntity<Void> error(@PathVariable("api") String api) {
			switch (api) {
			case "brave":
				tracer.currentSpanCustomizer().tag("error", "user message");
				break;
			case "opentracing":
				opentracing.activeSpan().setTag(Tags.ERROR, true);
				break;
			case "exception":
				tracer.currentSpan().error(new RuntimeException("uncaught!"));
				break;
			default:
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		@RequestMapping("/badrequest")
		public ResponseEntity<Void> badrequest() {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		@RequestMapping("throws")
		public void toss() {
			throw new IllegalStateException("boom");
		}

	}

	static final class SpanRecord {

		private final String name;

		private final long startMillis;

		private final long durationMillis;

		private final String source;

		private final UUID traceId;

		private final UUID spanId;

		private final List<UUID> parents;

		private final List<UUID> followsFrom;

		private final List<Pair<String, String>> tags;

		private final List<SpanLog> spanLogs;

		SpanRecord(String name, long startMillis, long durationMillis, String source, UUID traceId, UUID spanId,
				List<UUID> parents, List<UUID> followsFrom, List<Pair<String, String>> tags, List<SpanLog> spanLogs) {
			this.name = name;
			this.startMillis = startMillis;
			this.durationMillis = durationMillis;
			this.source = source;
			this.traceId = traceId;
			this.spanId = spanId;
			this.parents = parents;
			this.followsFrom = followsFrom;
			this.tags = tags;
			this.spanLogs = spanLogs;
		}

		@Override
		public String toString() {
			return "SpanRecord{" + "name='" + name + '\'' + ", traceId=" + traceId + ", spanId=" + spanId + '}';
		}

	}

}
