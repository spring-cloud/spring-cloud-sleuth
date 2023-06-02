/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.instrument.web.client.reactor.netty;

import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
		properties = { "spring.sleuth.reactor.netty.debug.enabled=true",
				"spring.sleuth.reactor.instrumentation-type=decorate_queues", "spring.sleuth.sampler.probability=1.0",
				"server.port=8553" })
class ReuseTraceIdTest {

	private static final String TOKEN = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImdpUFkxeHZYb0taTVN3eDcvV1dHSUpQQjByTSJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImV4cCI6MTQ4OTA4MTA3OTc5OTB9.K-Bp_9lAW2W9PjmXGdGwrEID-dvamTmZwwndcQvO8mJJgW_PIo76_BYO-Ncstb_vPnxdG2Re9X_kEjve-i5cymdIkoYczPlryg-QxgLa1ZUt0-7FkLhWbGxghNLxk2k5vdcS07OwOG6wdDhoEvv_49h05p4FKG2Re5dskJIXRKzvmBYddqJrBDJsfYT0UGB94oVKnOQtI7mEB4Q1XpKz5NqYMN_HWZqEF5MHBBLbsIxCpHD6zOeJNppl7BSywFyJMRp-eSBwKlsR3eSX_jMDuM13Eaf3h3yd2pKDIxtValh822xaL9GnDq-YeAxmQrEg8o6tN_r1WRcoS8-cgqw3Ng";

	@Autowired
	private Tracer tracer;

	private final Set<String> traceIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private final Set<String> previousTraceIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private final Map<String, Boolean> failedTraceIds = new ConcurrentHashMap<>();

	private final Set<Exception> exceptions = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private final OkHttpClient client = new OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build();

	@Test
	void should_not_reuse_traces() throws InterruptedException {
		traceIds.clear();
		failedTraceIds.clear();
		exceptions.clear();
		int threads = 10;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		for (int i = 0; i < threads; i++) {
			pool.submit(this::testLoop);
		}
		pool.shutdown();
		pool.awaitTermination(1, TimeUnit.MINUTES);
		if (!failedTraceIds.isEmpty()) {
			System.out.println("The following trace ids failed:");
			failedTraceIds.forEach((id, flag) -> System.out.println("id=" + id + ", previous=" + flag));
			System.out.println("Previous trace ids:");
			previousTraceIds.forEach(System.out::println);
			System.out.println("trace ids: " + traceIds.size());
			System.out.println("failed trace ids: " + failedTraceIds.size());
			System.out.println("previous ids: " + previousTraceIds.size());
			fail();
		}
		if (!exceptions.isEmpty()) {
			System.out.println("The following exceptions occurred:");
			exceptions.forEach(Exception::printStackTrace);
			fail();
		}
	}

	private void testLoop() {
		Random rand = new Random();
		int iterations = 100;
		for (int i = 0; i < iterations; i++) {
			testOneTrace();
			try {
				Thread.sleep(rand.nextInt(100));
			}
			catch (InterruptedException e) {
				return;
			}
		}
	}

	private void testOneTrace() {
		Span span = tracer.newTrace();
		try (SpanInScope ws = tracer.withSpanInScope(span)) {
			String traceId = tracer.currentSpan().context().traceIdString();
			traceIds.add(traceId);
			System.out.println("testing traceId= " + traceId);
			String spanId = tracer.currentSpan().context().spanIdString();
			URL url = new URL("http://localhost:8553/headers");
			Request request = new Request.Builder().url(url).header("X-B3-TraceId", traceId)
					.header("X-B3-SpanId", spanId).header("Authorization", "Bearer " + TOKEN)
					.header("Content-Type", "application/json").build();

			Call call = client.newCall(request);
			try (Response clientResponse = call.execute()) {
				int status = clientResponse.code();
				if (status != 200) {
					throw new RuntimeException("Request failed with status " + status);
				}

				String responseTraceId = clientResponse.headers().get("X-DOX-TraceId");
				if (!traceId.equals(responseTraceId)) {
					System.out.println("error for traceId= " + traceId);
					boolean previous = traceIds.contains(responseTraceId);
					if (previous) {
						previousTraceIds.add(responseTraceId);
						System.out.println("received for " + traceId + " a previous trace id " + responseTraceId);
					}
					else {
						System.out.println("received an unknown trace id: " + responseTraceId);
					}
					failedTraceIds.put(traceId, previous);
				}
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			exceptions.add(ex);
		}
		finally {
			span.finish();
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class Config {

		WireMockServer wireMockServer = new WireMockServer(
				options().dynamicPort().extensions(new ResponseTemplateTransformer(false))) {
			{
				start();
			}
		};

		@PostConstruct
		void setup() {
			wireMockServer.stubFor(WireMock.get("/headers")
					.willReturn(WireMock.aResponse().withHeader("x-dox-traceId", "{{request.headers.x-b3-traceid}}")
							.withTransformers("response-template")));
		}

		@PreDestroy
		void clean() {
			wireMockServer.shutdown();
		}

		@Bean
		RouteLocator builder(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("test_route",
							r -> r.path("/headers/**").uri("http://localhost:" + wireMockServer.port() + "/headers"))
					.build();
		}

	}

}
