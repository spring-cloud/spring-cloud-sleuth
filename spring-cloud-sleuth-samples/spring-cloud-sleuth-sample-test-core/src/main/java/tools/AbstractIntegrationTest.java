/*
 * Copyright 2013-2015 the original author or authors.
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
package tools;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.BDDAssertions.then;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.cloud.sleuth.zipkin.HttpZipkinSpanReporter;
import org.springframework.cloud.sleuth.zipkin.ZipkinProperties;
import org.springframework.cloud.sleuth.zipkin.ZipkinSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.core.ConditionFactory;

import io.zipkin.Codec;
import io.zipkin.Span;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Marcin Grzejszczak
 */
@Slf4j
public abstract class AbstractIntegrationTest {

	protected static int pollInterval = 1;
	protected static int timeout = 120;
	protected RestTemplate restTemplate = new AssertingRestTemplate();

	public static ConditionFactory await() {
		return Awaitility.await().pollInterval(pollInterval, SECONDS).atMost(timeout, SECONDS);
	}

	protected long zipkinHashedTraceId(String string) {
		long h = 1125899906842597L;
		if (string == null) {
			return h;
		}
		int len = string.length();

		for (int i = 0; i < len; i++) {
			h = 31 * h + string.charAt(i);
		}
		return h;
	}

	protected String zipkinHashedHexStringTraceId(String traceId) {
		long hashedTraceId = zipkinHashedTraceId(traceId);
		return Long.toHexString(hashedTraceId);
	}

	protected Runnable zipkinQueryServerIsUp() {
		return checkServerHealth("Zipkin Query Server", this::endpointToCheckZipkinQueryHealth);
	}

	protected Runnable zipkinServerIsUp() {
		return checkServerHealth("Zipkin Stream Server", this::endpointToCheckZipkinServerHealth);
	}

	protected Runnable checkServerHealth(String appName, RequestExchanger requestExchanger) {
		return () -> {
			ResponseEntity<String> response = requestExchanger.exchange();
			log.info("Response from the [{}] health endpoint is [{}]", appName, response);
			then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			log.info("[{}] is up!", appName);
		};
	}

	private interface RequestExchanger {
		ResponseEntity<String> exchange();
	}

	protected ResponseEntity<String> endpointToCheckZipkinQueryHealth() {
		URI uri = URI.create(getZipkinServicesQueryUrl());
		log.info("Sending request to the Zipkin query service [{}]", uri);
		return exchangeRequest(uri);
	}

	protected ResponseEntity<String> endpointToCheckZipkinServerHealth() {
		URI uri = URI.create("http://localhost:9411/health");
		log.info("Sending request to the Zipkin Server [{}]", uri);
		return exchangeRequest(uri);
	}

	protected ResponseEntity<String> checkStateOfTheTraceId(String traceId) {
		String hexTraceId = zipkinHashedHexStringTraceId(traceId);
		URI uri = URI.create(getZipkinTraceQueryUrl() + hexTraceId);
		log.info("Sending request to the Zipkin query service [{}]. Checking presence of trace id [{}] and its hex version [{}]", uri, traceId, hexTraceId);
		return exchangeRequest(uri);
	}

	protected ResponseEntity<String> exchangeRequest(URI uri) {
		return this.restTemplate.exchange(
				new RequestEntity<>(new HttpHeaders(), HttpMethod.GET, uri), String.class
		);
	}

	protected String getZipkinTraceQueryUrl() {
		return "http://localhost:9411/api/v1/trace/";
	}

	protected String getZipkinServicesQueryUrl() {
		return "http://localhost:9411/api/v1/services";
	}

	protected Runnable httpMessageWithTraceIdInHeadersIsSuccessfullySent(String endpoint, String traceId) {
		return new RequestSendingRunnable(this.restTemplate, endpoint, traceId);
	}

	protected Runnable allSpansWereRegisteredInZipkinWithTraceIdEqualTo(String traceId) {
		return () -> {
			ResponseEntity<String> response = checkStateOfTheTraceId(traceId);
			log.info("Response from the Zipkin query service about the trace id [{}] for trace with id [{}]", response, traceId);
			then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			then(response.hasBody()).isTrue();
			List<Span> spans = Codec.JSON.readSpans(response.getBody().getBytes());
			List<String> serviceNamesNotFoundInZipkin = serviceNamesNotFoundInZipkin(spans);
			List<String> spanNamesNotFoundInZipkin = annotationsNotFoundInZipkin(spans);
			log.info("The following services were not found in Zipkin {}", serviceNamesNotFoundInZipkin);
			log.info("The following spans were not found in Zipkin {}", spanNamesNotFoundInZipkin);
			then(serviceNamesNotFoundInZipkin).isEmpty();
			then(spanNamesNotFoundInZipkin).isEmpty();
			log.info("Zipkin tracing is working! Sleuth is working! Let's be happy!");
		};
	}

	protected List<String> serviceNamesNotFoundInZipkin(List<io.zipkin.Span> spans) {
		List<String> serviceNamesFoundInAnnotations = spans.stream()
				.filter(span -> span.annotations != null)
				.map(span -> span.annotations)
				.flatMap(Collection::stream)
				.filter(span -> span.endpoint != null)
				.map(annotation -> annotation.endpoint)
				.map(endpoint -> endpoint.serviceName)
				.distinct()
				.collect(Collectors.toList());
		List<String> serviceNamesFoundInBinaryAnnotations = spans.stream()
				.filter(span -> span.binaryAnnotations != null)
				.map(span -> span.binaryAnnotations)
				.flatMap(Collection::stream)
				.filter(span -> span.endpoint != null)
				.map(annotation -> annotation.endpoint)
				.map(endpoint -> endpoint.serviceName)
				.distinct()
				.collect(Collectors.toList());
		List<String> names = new ArrayList<>();
		names.addAll(serviceNamesFoundInAnnotations);
		names.addAll(serviceNamesFoundInBinaryAnnotations);
		return names.contains(getAppName()) ? Collections.emptyList() : names;
	}

	protected String getAppName() {
		return "unknown";
	}

	protected List<String> annotationsNotFoundInZipkin(List<io.zipkin.Span> spans) {
		String binaryAnnotationName = getRequiredBinaryAnnotationName();
		Optional<String> names = spans.stream()
				.filter(span -> span.binaryAnnotations != null)
				.map(span -> span.binaryAnnotations)
				.flatMap(Collection::stream)
				.filter(span -> span.endpoint != null)
				.map(annotation -> annotation.key)
				.filter(binaryAnnotationName::equals)
				.findFirst();
		return names.isPresent() ? Collections.emptyList() : Collections.singletonList(binaryAnnotationName);
	}

	protected String getRequiredBinaryAnnotationName() {
		return "random-sleep-millis";
	}

	@Configuration
	public static class ZipkinSpanReporterConfig {
		@Bean
		ZipkinSpanReporter integrationTestZipkinSpanReporter() {
			return new IntegrationTestZipkinSpanReporter();
		}
	}

	@Configuration
	@Slf4j
	public static class WaitUntilZipkinIsUpConfig {
		@Bean
		@SneakyThrows
		public ZipkinSpanReporter reporter(final ZipkinProperties zipkin) {
			await().until(new Runnable() {
				@Override
				public void run() {
					try {
						WaitUntilZipkinIsUpConfig.this.getZipkinSpanReporter(zipkin);
					} catch (Exception e) {
						log.error("Exception occurred while trying to connect to zipkin [" + e.getCause() + "]");
						throw new AssertionError(e);
					}
				}
			});
			return getZipkinSpanReporter(zipkin);
		}

		private ZipkinSpanReporter getZipkinSpanReporter(ZipkinProperties zipkin) {
			String url = "http://localhost:" + zipkin.getPort();
			return new HttpZipkinSpanReporter(url, zipkin.getFlushInterval());
		}
	}
}
