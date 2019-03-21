/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.cloud.sleuth.zipkin.stream;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.assertj.core.api.Condition;
import org.junit.Test;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.Host;
import org.springframework.cloud.sleuth.stream.Spans;
import zipkin.Constants;
import zipkin.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

public class ConvertToZipkinSpanListTests {

	Host host = new Host("myservice", "1.2.3.4", 8080);

	@Test
	public void skipsInputSpans() {
		Spans spans = new Spans(this.host,
				Collections.singletonList(span("sleuth")));

		List<zipkin.Span> result = ConvertToZipkinSpanList.convert(spans);

		assertThat(result).isEmpty();
	}

	@Test
	public void nullEndpointPort() {
		this.host.setPort(0);
		Span span = span("sleuth");
		span.logEvent(Constants.CLIENT_SEND);
		zipkin.Span result = ConvertToZipkinSpanList.convert(span, host);
		assertThat(result).isNotNull();
	}

	@Test
	public void retainsValidSpans() {
		Spans spans = new Spans(this.host,
				Arrays.asList(span("foo"), span("bar"), span("baz")));

		List<zipkin.Span> result = ConvertToZipkinSpanList.convert(spans);

		assertThat(result).extracting(s -> s.name).containsExactly(
				"message:foo", "message:bar", "message:baz");
	}

	@Test
	public void appendsLocalComponentTagIfNoZipkinLogIsPresent() {
		Spans spans = new Spans(this.host, Collections.singletonList(span("foo")));

		List<zipkin.Span> result = ConvertToZipkinSpanList.convert(spans);

		assertThat(result)
				.flatExtracting(s -> s.binaryAnnotations)
				.extracting(input -> input.key)
				.contains(Constants.LOCAL_COMPONENT);
	}

	@Test
	public void appendServerAddressTagIfClientLogIsPresentWhenPeerServiceIsPresent() {
		Span span = span("foo");
		span.logEvent(Constants.CLIENT_SEND);
		span.tag(Span.SPAN_PEER_SERVICE_TAG_NAME, "myservice");
		Spans spans = new Spans(this.host, Collections.singletonList(span));

		List<zipkin.Span> result = ConvertToZipkinSpanList.convert(spans);

		assertThat(result)
				.hasSize(1)
				.flatExtracting(input1 -> input1.binaryAnnotations)
				.filteredOn("key", Constants.SERVER_ADDR)
				.extracting(input -> input.endpoint)
				.hasSize(1)
				.has(new Condition<List<? extends Endpoint>>() {
					@Override public boolean matches(List<? extends Endpoint> value) {
						Endpoint endpoint = value.get(0);
						return endpoint.serviceName.equals("myservice") && endpoint.ipv4 == 0;
					}
				});
	}

	@Test
	public void doesNotAppendServerAddressTagIfClientLogIsPresent() {
		Span span = span("foo");
		span.logEvent(Constants.CLIENT_SEND);
		Spans spans = new Spans(this.host, Collections.singletonList(span));

		List<zipkin.Span> result = ConvertToZipkinSpanList.convert(spans);

		assertThat(result)
				.hasSize(1)
				.flatExtracting(input1 -> input1.binaryAnnotations)
				.filteredOn("key", Constants.SERVER_ADDR)
				.isEmpty();
	}

	@Test
	public void shouldReuseServerAddressTag() {
		Span span = span("foo");
		span.logEvent(Constants.CLIENT_SEND);
		span.tag(Span.SPAN_PEER_SERVICE_TAG_NAME, "barservice");
		Spans spans = new Spans(this.host, Collections.singletonList(span));

		List<zipkin.Span> result = ConvertToZipkinSpanList.convert(spans);

		assertThat(result)
				.hasSize(1)
				.flatExtracting(input1 -> input1.binaryAnnotations)
				.filteredOn("key", Constants.SERVER_ADDR)
				.extracting(input -> input.endpoint.serviceName)
				.contains("barservice");
	}

	/** Sleuth timestamps are millisecond granularity while zipkin is microsecond. */
	@Test
	public void convertsTimestampToMicrosecondsAndSetsDurationToAccumulatedMicros() {
		long start = System.currentTimeMillis();
		Span span = span("foo");
		span.logEvent(Constants.CLIENT_SEND);
		span.stop();

		Spans spans = new Spans(this.host, Collections.singletonList(span));
		zipkin.Span result = ConvertToZipkinSpanList.convert(spans).get(0);

		assertThat(result.timestamp)
				.isEqualTo(span.getBegin() * 1000);
		assertThat(result.duration)
				.isEqualTo(span.getAccumulatedMicros());
		assertThat(result.annotations.get(0).timestamp)
				.isGreaterThanOrEqualTo(start * 1000)
				.isLessThanOrEqualTo(System.currentTimeMillis() * 1000);
	}

	@Test
	public void setsTheDurationToTheDifferenceBetweenCRandCS()
			throws InterruptedException {
		Span span = span("foo");
		span.logEvent(Span.CLIENT_SEND);
		Thread.sleep(10);
		span.logEvent(Span.CLIENT_RECV);
		Thread.sleep(20);
		span.stop();

		Spans spans = new Spans(this.host, Collections.singletonList(span));
		zipkin.Span result = ConvertToZipkinSpanList.convert(spans).get(0);

		assertThat(result.timestamp)
				.isEqualTo(span.getBegin() * 1000);
		long clientSendTimestamp = span.logs().stream().filter(log -> Span.CLIENT_SEND.equals(log.getEvent()))
				.findFirst().get().getTimestamp();
		long clientRecvTimestamp = span.logs().stream().filter(log -> Span.CLIENT_RECV.equals(log.getEvent()))
				.findFirst().get().getTimestamp();
		assertThat(result.duration)
				.isNotEqualTo(span.getAccumulatedMicros())
				.isEqualTo((clientRecvTimestamp - clientSendTimestamp) * 1000);
	}

	/** Zipkin's duration should only be set when the span is finished. */
	@Test
	public void doesntSetDurationWhenStillRunning() {
		Span running = Span.builder().traceId(1L).name("http:child").build();
		Spans spans = new Spans(this.host, Collections.singletonList(running));
		zipkin.Span result = ConvertToZipkinSpanList.convert(spans).get(0);

		assertThat(result.timestamp)
				.isGreaterThan(0); // sanity check it did start
		assertThat(result.duration)
				.isNull();
	}

	/**
	 * In the RPC span model, the client owns the timestamp and duration of the span. If we
	 * were propagated an id, we can assume that we shouldn't report timestamp or duration,
	 * rather let the client do that. Worst case we were propagated an unreported ID and
	 * Zipkin backfills timestamp and duration.
	 */
	@Test
	public void doesntSetTimestampOrDurationWhenRemote() {
		Span span = span("foo", true);
		Spans spans = new Spans(this.host, Collections.singletonList(span));
		zipkin.Span result = ConvertToZipkinSpanList.convert(spans).get(0);

		assertThat(result.timestamp)
				.isNull();
		assertThat(result.duration)
				.isNull();
	}

	@Test
	public void converts128BitTraceId() {
		Span span = Span.builder().traceIdHigh(1L).traceId(2L).spanId(3L).name("foo").build();

		Spans spans = new Spans(this.host, Collections.singletonList(span));
		zipkin.Span result = ConvertToZipkinSpanList.convert(spans).get(0);

		assertThat(result.traceIdHigh).isEqualTo(span.getTraceIdHigh());
		assertThat(result.traceId).isEqualTo(span.getTraceId());
	}

	@Test
	public void shouldRemoveTimestampAndDurationForNonRemoteSharedSpan() {
		Span span = Span.builder()
				.name("foo")
				.exportable(false)
				.remote(false)
				.shared(true)
				.build();
		Spans spans = new Spans(this.host, Collections.singletonList(span));

		zipkin.Span result = ConvertToZipkinSpanList.convert(spans).get(0);

		assertThat(result.duration).isNull();
		assertThat(result.timestamp).isNull();
	}

	@Test
	public void shouldNotRemoveTimestampAndDurationForNonRemoteNonSharedSpan() {
		Span span = Span.builder()
				.name("foo")
				.exportable(false)
				.remote(false)
				.shared(false)
				.build();
		span.stop();
		Spans spans = new Spans(this.host, Collections.singletonList(span));

		zipkin.Span result = ConvertToZipkinSpanList.convert(spans).get(0);

		assertThat(result.duration).isNotNull();
		assertThat(result.timestamp).isNotNull();
	}

	Span span(String name) {
		return span(name, false);
	}

	Span span(String name, boolean remote) {
		Long id = new Random().nextLong();
		return Span.builder().begin(1).end(3).name("message:" + name).traceId(id).spanId(id)
				.remote(remote).processId("process").build();
	}
}
