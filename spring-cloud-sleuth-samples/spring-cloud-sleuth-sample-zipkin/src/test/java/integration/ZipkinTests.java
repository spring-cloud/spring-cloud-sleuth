/*
 * Copyright 2013-2018 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import brave.sampler.Sampler;
import integration.ZipkinTests.WaitUntilZipkinIsUpConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import sample.SampleZipkinApplication;
import tools.AbstractIntegrationTest;
import tools.SpanUtil;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.zipkin2.ZipkinProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = { WaitUntilZipkinIsUpConfig.class, SampleZipkinApplication.class },
		webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {"sample.zipkin.enabled=true"})
public class ZipkinTests extends AbstractIntegrationTest {
	@ClassRule public static final MockWebServer zipkin = new MockWebServer();

	private static final String APP_NAME = "testsleuthzipkin";
	@Value("${local.server.port}")
	private int port = 3380;
	private String sampleAppUrl = "http://localhost:" + this.port;
	@Autowired ZipkinProperties zipkinProperties;

	@Test
	public void should_propagate_spans_to_zipkin() throws Exception {
		zipkin.enqueue(new MockResponse());

		long traceId = new Random().nextLong();

		await().atMost(10, SECONDS).untilAsserted(() ->
				httpMessageWithTraceIdInHeadersIsSuccessfullySent(
						this.sampleAppUrl + "/hi2", traceId).run()
		);

		spansSentToZipkin(zipkin, traceId);
	}

  String getAppName() {
		return APP_NAME;
	}

	@Configuration
	public static class WaitUntilZipkinIsUpConfig {

		@Bean
		@Primary
		ZipkinProperties testZipkinProperties() {
			ZipkinProperties zipkinProperties = new ZipkinProperties();
			zipkinProperties.setBaseUrl(zipkin.url("/").toString());
			return zipkinProperties;
		}

		@Bean Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}
	}

	void spansSentToZipkin(MockWebServer zipkin, long traceId)
			throws InterruptedException {
		RecordedRequest request = zipkin.takeRequest();
		List<Span> spans = SpanBytesDecoder.JSON_V2.decodeList(request.getBody().readByteArray());
		List<String> traceIdsNotFoundInZipkin = traceIdsNotFoundInZipkin(spans, traceId);
		List<String> serviceNamesNotFoundInZipkin = serviceNamesNotFoundInZipkin(spans);
		List<String> tagsNotFoundInZipkin = hasRequiredTag(spans);
		log.info(String.format("The following trace IDs were not found in Zipkin [%s]", traceIdsNotFoundInZipkin));
		log.info(String.format("The following services were not found in Zipkin [%s]", serviceNamesNotFoundInZipkin));
		log.info(String.format("The following tags were not found in Zipkin [%s]", tagsNotFoundInZipkin));
		then(traceIdsNotFoundInZipkin).isEmpty();
		then(serviceNamesNotFoundInZipkin).isEmpty();
		then(tagsNotFoundInZipkin).isEmpty();
		log.info("Zipkin tracing is working! Sleuth is working! Let's be happy!");
	}

	List<String> traceIdsNotFoundInZipkin(List<Span> spans, long traceId) {
		String traceIdString = SpanUtil.idToHex(traceId);
		Optional<String> traceIds = spans.stream()
				.map(Span::traceId)
				.filter(traceIdString::equals)
				.findFirst();
		return traceIds.isPresent() ? Collections.emptyList() : Collections.singletonList(traceIdString);
	}

	List<String> serviceNamesNotFoundInZipkin(List<Span> spans) {
		List<String> localServiceNames = spans.stream()
				.map(Span::localServiceName)
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());
		List<String> remoteServiceNames = spans.stream()
				.map(Span::remoteServiceName)
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());
		List<String> names = new ArrayList<>();
		names.addAll(localServiceNames);
		names.addAll(remoteServiceNames);
		return names.contains(getAppName()) ? Collections.emptyList() : names;
	}

	List<String> hasRequiredTag(List<Span> spans) {
		String key = getRequiredTagKey();
		Optional<String> keys = spans.stream()
				.flatMap(span -> span.tags().keySet().stream())
				.filter(key::equals)
				.findFirst();
		return keys.isPresent() ? Collections.emptyList() : Collections.singletonList(key);
	}

	String getRequiredTagKey() {
		return "random-sleep-millis";
	}
}
