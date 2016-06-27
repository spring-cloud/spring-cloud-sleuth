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

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.core.ConditionFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.springframework.cloud.sleuth.trace.IntegrationTestSpanContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import zipkin.Codec;
import zipkin.Span;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public abstract class AbstractIntegrationTest {

	protected static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	protected static final int POLL_INTERVAL = 1;
	protected static final int TIMEOUT = 20;
	protected RestTemplate restTemplate = new AssertingRestTemplate();

	@Before
	public void clearSpanBefore() {
		IntegrationTestSpanContextHolder.removeCurrentSpan();
	}

	@After
	public void clearSpanAfter() {
		IntegrationTestSpanContextHolder.removeCurrentSpan();
	}

	public static ConditionFactory await() {
		return Awaitility.await().pollInterval(POLL_INTERVAL, SECONDS).atMost(TIMEOUT, SECONDS);
	}

	protected Runnable zipkinServerIsUp() {
		return checkServerHealth("Zipkin Stream Server", this::endpointToCheckZipkinServerHealth);
	}

	protected Runnable checkServerHealth(String appName, RequestExchanger requestExchanger) {
		return () -> {
			ResponseEntity<String> response = requestExchanger.exchange();
			log.info(String.format("Response from the [%s] health endpoint is [%s]", appName, response));
			then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			log.info(String.format("[%s] is up!", appName));
		};
	}

	private interface RequestExchanger {
		ResponseEntity<String> exchange();
	}

	protected ResponseEntity<String> endpointToCheckZipkinServerHealth() {
		URI uri = URI.create("http://localhost:" +getZipkinServerPort()+"/health");
		log.info(String.format("Sending request to the Zipkin Server [%s]", uri));
		return exchangeRequest(uri);
	}

	protected int getZipkinServerPort() {
		return 9411;
	}

	protected ResponseEntity<String> checkStateOfTheTraceId(long traceId) {
		URI uri = URI.create(getZipkinTraceQueryUrl() + Long.toHexString(traceId));
		log.info(String.format("Sending request to the Zipkin query service [%s]. "
				+ "Checking presence of trace id [%d]", uri, traceId));
		return exchangeRequest(uri);
	}

	protected ResponseEntity<String> exchangeRequest(URI uri) {
		return this.restTemplate.exchange(
				new RequestEntity<>(new HttpHeaders(), HttpMethod.GET, uri), String.class
		);
	}

	protected String getZipkinTraceQueryUrl() {
		return "http://localhost:"+getZipkinServerPort()+"/api/v1/trace/";
	}

	protected String getZipkinServicesQueryUrl() {
		return "http://localhost:"+getZipkinServerPort()+"/api/v1/services";
	}

	protected Runnable httpMessageWithTraceIdInHeadersIsSuccessfullySent(String endpoint, long traceId) {
		return new RequestSendingRunnable(this.restTemplate, endpoint, traceId, null);
	}

	protected Runnable httpMessageWithTraceIdInHeadersIsSuccessfullySent(String endpoint, long traceId, Long spanId) {
		return new RequestSendingRunnable(this.restTemplate, endpoint, traceId, spanId);
	}

	protected Runnable allSpansWereRegisteredInZipkinWithTraceIdEqualTo(long traceId) {
		return () -> {
			ResponseEntity<String> response = checkStateOfTheTraceId(traceId);
			log.info(String.format("Response from the Zipkin query service about the "
					+ "trace id [%s] for trace with id [%d]", response, traceId));
			then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			then(response.hasBody()).isTrue();
			List<Span> spans = Codec.JSON.readSpans(response.getBody().getBytes());
			List<String> serviceNamesNotFoundInZipkin = serviceNamesNotFoundInZipkin(spans);
			List<String> spanNamesNotFoundInZipkin = annotationsNotFoundInZipkin(spans);
			log.info(String.format("The following services were not found in Zipkin [%s]", serviceNamesNotFoundInZipkin));
			log.info(String.format("The following annotations were not found in Zipkin [%s]", spanNamesNotFoundInZipkin));
			then(serviceNamesNotFoundInZipkin).isEmpty();
			then(spanNamesNotFoundInZipkin).isEmpty();
			log.info("Zipkin tracing is working! Sleuth is working! Let's be happy!");
		};
	}

	protected List<String> serviceNamesNotFoundInZipkin(List<zipkin.Span> spans) {
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

	protected List<String> annotationsNotFoundInZipkin(List<zipkin.Span> spans) {
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

}
