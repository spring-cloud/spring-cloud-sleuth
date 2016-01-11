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
package integration;

import io.zipkin.Codec;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.JdkIdGenerator;
import org.testcontainers.containers.DockerComposeContainer;
import sample.SampleZipkinApplication;
import tools.AbstractIntegrationTest;
import tools.RequestSendingRunnable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { AbstractIntegrationTest.ZipkinConfig.class, SampleZipkinApplication.class })
@WebIntegrationTest
@TestPropertySource(properties="sample.zipkin.enabled=true")
@Slf4j
public class ZipkinDockerTests extends AbstractIntegrationTest {

	private static final String APP_NAME = "testsleuthzipkin";
	private static int port = 3380;
	private static String sampleAppUrl = "http://localhost:" + port;

	@ClassRule
	public static DockerComposeContainer environment =
			new DockerComposeContainer(new File("src/test/resources/docker-compose.yml"))
					.withExposedService("rabbitmq_1", 5672)
					.withExposedService("collector_1", 9410)
					.withExposedService("collector_1", 9900)
					.withExposedService("mysql_1", 3306)
					.withExposedService("query_1", 9411)
					.withExposedService("query_1", 9901);

	@Before
	public void setup() {
		await().until(zipkinQueryServerIsUp());
		await().until(zipkinCollectorServerIsUp());
	}

	@Test
	@SneakyThrows
	public void should_propagate_spans_to_zipkin() {
		String traceId = new JdkIdGenerator().generateId().toString();

		httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/hi2", traceId);

		await().until(() -> {
			allSpansWereRegisteredInZipkinWithTraceIdEqualTo(traceId);
		});
	}

	private void allSpansWereRegisteredInZipkinWithTraceIdEqualTo(String traceId) {
		ResponseEntity<String> response = checkStateOfTheTraceId(traceId);
		log.info("Response from the Zipkin query service about the trace id [{}] for trace with id [{}]", response, traceId);
		then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		then(response.hasBody()).isTrue();
		List<io.zipkin.Span> spans = Codec.JSON.readSpans(response.getBody().getBytes());
		List<String> serviceNamesNotFoundInZipkin = serviceNamesNotFoundInZipkin(spans);
		List<String> spanNamesNotFoundInZipkin = annotationsNotFoundInZipkin(spans);
		log.info("The following services were not found in Zipkin {}", serviceNamesNotFoundInZipkin);
		log.info("The following spans were not found in Zipkin {}", spanNamesNotFoundInZipkin);
		then(serviceNamesNotFoundInZipkin).isEmpty();
		then(spanNamesNotFoundInZipkin).isEmpty();
		log.info("Zipkin tracing is working! Sleuth is working! Let's be happy!");
	}

	private List<String> serviceNamesNotFoundInZipkin(List<io.zipkin.Span> spans) {
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
		return names.contains(APP_NAME) ? Collections.EMPTY_LIST : names;
	}

	private List<String> annotationsNotFoundInZipkin(List<io.zipkin.Span> spans) {
		String binaryAnnotationName = "random-sleep-millis";
		Optional<String> names = spans.stream()
				.filter(span -> span.binaryAnnotations != null)
				.map(span -> span.binaryAnnotations)
				.flatMap(Collection::stream)
				.filter(span -> span.endpoint != null)
				.map(annotation -> annotation.key)
				.filter(binaryAnnotationName::equals)
				.findFirst();
		return names.isPresent() ? Collections.EMPTY_LIST : Collections.singletonList(binaryAnnotationName);
	}

	private void httpMessageWithTraceIdInHeadersIsSuccessfullySent(String endpoint, String traceId) {
		new RequestSendingRunnable(restTemplate, endpoint, traceId).run();
	}

}
