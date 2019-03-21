/*
 * Copyright 2013-2015 the original author or authors.
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

import org.junit.Test;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.Host;
import zipkin.BinaryAnnotation;
import zipkin.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinMessageListenerTests {
	Span span = Span.builder().begin(1).end(3).name("http:name").traceId(1L).spanId(2L).remote(true)
			.exportable(true).processId("process").build();
	Host host = new Host("myservice", "1.2.3.4", 8080);
	Endpoint endpoint = Endpoint.builder()
			.serviceName("myservice")
			.ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4)
			.port(8080).build();

	/**
	 * In the RPC span model, the client owns the timestamp and duration of the span. If we
	 * were propagated an id, we can assume that we shouldn't report timestamp or duration,
	 * rather let the client do that. Worst case we were propagated an unreported ID and
	 * Zipkin backfills timestamp and duration.
	 */
	@Test
	public void doesntSetTimestampOrDurationWhenRemote() {
		this.span.stop();
		zipkin.Span result = ConvertToZipkinSpanList.convert(this.span, this.host);

		assertThat(result.timestamp)
				.isNull();
		assertThat(result.duration)
				.isNull();
	}

	/** Sleuth timestamps are millisecond granularity while zipkin is microsecond. */
	@Test
	public void convertsTimestampAndDurationToMicroseconds() {
		Span span = Span.builder().begin(1).end(3).name("http:name").traceId(1L).spanId(2L).remote(false)
				.exportable(true).processId("process").build();
		long start = System.currentTimeMillis();
		span.logEvent("hystrix/retry"); // System.currentTimeMillis

		zipkin.Span result = ConvertToZipkinSpanList.convert(span, this.host);

		assertThat(result.timestamp)
				.isEqualTo(span.getBegin() * 1000);
		assertThat(result.duration)
				.isEqualTo((span.getEnd() - span.getBegin()) * 1000);
		assertThat(result.annotations.get(0).timestamp)
				.isGreaterThanOrEqualTo(start * 1000)
				.isLessThanOrEqualTo(System.currentTimeMillis() * 1000);
	}

	/** Sleuth host corresponds to annotation/binaryAnnotation.host in zipkin. */
	@Test
	public void annotationsIncludeHost() {
		this.span.logEvent("hystrix/retry");
		this.span.tag("spring-boot/version", "1.3.1.RELEASE");

		zipkin.Span result = ConvertToZipkinSpanList.convert(this.span, this.host);

		assertThat(result.annotations.get(0).endpoint)
				.isEqualTo(this.endpoint);
		assertThat(result.binaryAnnotations.get(0).endpoint)
				.isEqualTo(result.annotations.get(0).endpoint);
	}

	/**
	 * In zipkin, the service context is attached to annotations. Sleuth spans
	 * that have no annotations will get an "lc" one, which allows them to be
	 * queryable in zipkin by service name.
	 */
	@Test
	public void spanWithoutAnnotationsLogsComponent() {
		zipkin.Span result = ConvertToZipkinSpanList.convert(this.span, this.host);

		assertThat(result.binaryAnnotations).hasSize(1);
		assertThat(result.binaryAnnotations.get(0)).isEqualToComparingFieldByField(
				BinaryAnnotation.create("lc", this.span.getProcessId(), this.endpoint));
	}

	// TODO: "unknown" bc process id, documented as not nullable, is null in some tests.
	@Test
	public void nullProcessIdCoercesToUnknownServiceName() {
		Span noProcessId = Span.builder().traceId(1L).name("http:parent").remote(true).build();

		zipkin.Span result = ConvertToZipkinSpanList.convert(noProcessId, this.host);

		assertThat(result.binaryAnnotations)
				.containsOnly(BinaryAnnotation.create("lc", "unknown", this.endpoint));
	}
}