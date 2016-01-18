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

import integration.MessagingApplicationTests.IntegrationSpanCollectorConfig;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.sleuth.util.RandomLongSpanIdGenerator;
import org.springframework.cloud.sleuth.zipkin.ZipkinSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import sample.SampleMessagingApplication;
import tools.AbstractIntegrationTest;

import java.util.Collection;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { IntegrationSpanCollectorConfig.class, SampleMessagingApplication.class })
@WebIntegrationTest
@TestPropertySource(properties="sample.zipkin.enabled=true")
public class MessagingApplicationTests extends AbstractIntegrationTest {

	private static int port = 3381;
	private static String sampleAppUrl = "http://localhost:" + port;
	@Autowired IntegrationTestZipkinSpanReporter integrationTestSpanCollector;

	@After
	public void cleanup() {
		this.integrationTestSpanCollector.hashedSpans.clear();
	}

	@Test
	public void should_propagate_spans_for_messaging() {
		long traceId = new RandomLongSpanIdGenerator().generateId();

		await().until(httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/", traceId));

		await().until(() -> {
			thenAllSpansHaveTraceIdEqualTo(traceId);
		});
	}

	@Test
	public void should_propagate_spans_for_messaging_with_async() {
		long traceId = new RandomLongSpanIdGenerator().generateId();

		await().until(httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/xform", traceId));

		await().until(() -> {
			thenAllSpansHaveTraceIdEqualTo(traceId);
			thenThereIsAtLeastOneBinaryAnnotationWithKey("background-sleep-millis");
		});
	}

	private void thenThereIsAtLeastOneBinaryAnnotationWithKey(String binaryAnnotationKey) {
		then(integrationTestSpanCollector.hashedSpans.stream()
				.map(s -> s.binaryAnnotations)
				.flatMap(Collection::stream)
				.anyMatch(b -> b.key.equals(binaryAnnotationKey))).isTrue();
	}

	private void thenAllSpansHaveTraceIdEqualTo(long traceId) {
		then(this.integrationTestSpanCollector.hashedSpans.stream().allMatch(span -> span.traceId == traceId)).isTrue();
	}

	@Configuration
	public static class IntegrationSpanCollectorConfig {
		@Bean
		ZipkinSpanReporter integrationTestZipkinSpanReporter() {
			return new IntegrationTestZipkinSpanReporter();
		}
	}

}
