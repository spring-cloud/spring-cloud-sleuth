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

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import brave.sampler.Sampler;
import integration.MessagingApplicationTests.IntegrationSpanCollectorConfig;
import sample.SampleMessagingApplication;
import tools.AbstractIntegrationTest;
import tools.SpanUtil;
import zipkin2.Span;
import zipkin2.reporter.Reporter;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = { IntegrationSpanCollectorConfig.class, SampleMessagingApplication.class },
		webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = { "sample.zipkin.enabled=true",
		"spring.sleuth.http.legacy.enabled=true" })
@DirtiesContext
public class MessagingApplicationTests extends AbstractIntegrationTest {

	private static int port = 3381;
	private static String sampleAppUrl = "http://localhost:" + port;
	@Autowired IntegrationTestZipkinSpanReporter integrationTestSpanCollector;

	@After
	public void cleanup() {
		this.integrationTestSpanCollector.hashedSpans.clear();
	}

	@Test
	public void should_have_passed_trace_id_when_message_is_about_to_be_sent() {
		long traceId = new Random().nextLong();

		await().atMost(15, SECONDS).untilAsserted(() ->
				httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/", traceId).run()
		);

		await().atMost(15, SECONDS).untilAsserted(() ->
			thenAllSpansHaveTraceIdEqualTo(traceId)
		);
	}

	@Test
	public void should_have_passed_trace_id_and_generate_new_span_id_when_message_is_about_to_be_sent() {
		long traceId = new Random().nextLong();
		long spanId = new Random().nextLong();

		await().atMost(15, SECONDS).untilAsserted(() ->
			httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/", traceId, spanId).run()
		);

		await().atMost(15, SECONDS).untilAsserted(() -> {
			thenAllSpansHaveTraceIdEqualTo(traceId);
			thenTheSpansHaveProperParentStructure();
		});
	}

	@Test
	public void should_have_passed_trace_id_with_annotations_in_async_thread_when_message_is_about_to_be_sent() {
		long traceId = new Random().nextLong();

		await().atMost(15, SECONDS).untilAsserted(() ->
				httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/xform", traceId).run()
		);

		await().atMost(15, SECONDS).untilAsserted(() -> {
			thenAllSpansHaveTraceIdEqualTo(traceId);
			thenThereIsAtLeastOneTagWithKey("background-sleep-millis");
		});
	}

	private void thenThereIsAtLeastOneTagWithKey(String key) {
		then(this.integrationTestSpanCollector.hashedSpans.stream()
				.map(Span::tags)
				.flatMap(m -> m.keySet().stream())
				.anyMatch(b -> b.equals(key))).isTrue();
	}

	private void thenAllSpansHaveTraceIdEqualTo(long traceId) {
		String traceIdHex = Long.toHexString(traceId);
		log.info("Stored spans: [\n" + this.integrationTestSpanCollector.hashedSpans
				.stream()
				.map(Span::toString)
				.collect(Collectors.joining("\n")) + "\n]");
		then(this.integrationTestSpanCollector.hashedSpans
				.stream()
				.filter(span -> !span.traceId().equals(SpanUtil.idToHex(traceId)))
				.collect(Collectors.toList()))
				.describedAs("All spans have same trace id [" + traceIdHex + "]")
				.isEmpty();
	}

	private void thenTheSpansHaveProperParentStructure() {
		Optional<Span> firstHttpSpan = findFirstHttpRequestSpan();
		List<Span> eventSpans = findAllEventRelatedSpans();
		Optional<Span> eventSentSpan = findSpanWithKind(Span.Kind.SERVER);
		Optional<Span> producerSpan = findSpanWithKind(Span.Kind.PRODUCER);
		Optional<Span> lastHttpSpansParent = findLastHttpSpansParent();
		// "http:/parent/" -> "message:messages" -> "http:/foo" (CS + CR) -> "http:/foo" (SS)
		thenAllSpansArePresent(firstHttpSpan, eventSpans, lastHttpSpansParent, eventSentSpan, producerSpan);
		then(this.integrationTestSpanCollector.hashedSpans).as("There were 5 spans").hasSize(5);
		log.info("Checking the parent child structure");
		List<Optional<Span>> parentChild = this.integrationTestSpanCollector.hashedSpans.stream()
				.filter(span -> span.parentId() != null)
				.map(span -> this.integrationTestSpanCollector.hashedSpans.stream().filter(span1 -> span1.id().equals(span.parentId())).findAny()
		).collect(Collectors.toList());
		log.info("List of parents and children " + parentChild);
		then(parentChild.stream().allMatch(Optional::isPresent)).isTrue();
	}

	private Optional<Span> findLastHttpSpansParent() {
		return this.integrationTestSpanCollector.hashedSpans.stream()
				.filter(span -> "http:/".equals(span.name()) && span.kind() != null).findFirst();
	}

	private Optional<Span> findSpanWithKind(Span.Kind kind) {
		return this.integrationTestSpanCollector.hashedSpans.stream()
				.filter(span -> kind.equals(span.kind()))
				.findFirst();
	}

	private List<Span> findAllEventRelatedSpans() {
		return this.integrationTestSpanCollector.hashedSpans.stream()
				.filter(span -> "send".equals(span.name()) && span.parentId() != null).collect(
						Collectors.toList());
	}

	private Optional<Span> findFirstHttpRequestSpan() {
		return this.integrationTestSpanCollector.hashedSpans.stream()
				// home is the name of the method
				.filter(span -> span.tags().values().stream()
						.anyMatch("home"::equals)).findFirst();
	}

	private void thenAllSpansArePresent(Optional<Span> firstHttpSpan,
			List<Span> eventSpans, Optional<Span> lastHttpSpan,
			Optional<Span> eventSentSpan, Optional<Span> eventReceivedSpan) {
		log.info("Found following spans");
		log.info("First http span " + firstHttpSpan);
		log.info("Event spans " + eventSpans);
		log.info("Event sent span " + eventSentSpan);
		log.info("Event received span " + eventReceivedSpan);
		log.info("Last http span " + lastHttpSpan);
		log.info("All found spans \n" + this.integrationTestSpanCollector.hashedSpans
				.stream().map(Span::toString).collect(Collectors.joining("\n")));
		then(firstHttpSpan.isPresent()).isTrue();
		then(eventSpans).isNotEmpty();
		then(eventSentSpan.isPresent()).isTrue();
		then(eventReceivedSpan.isPresent()).isTrue();
		then(lastHttpSpan.isPresent()).isTrue();
	}

	@Configuration
	public static class IntegrationSpanCollectorConfig {
		@Bean
		Reporter<Span> integrationTestZipkinSpanReporter() {
			return new IntegrationTestZipkinSpanReporter();
		}

		@Bean Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}
	}
}
