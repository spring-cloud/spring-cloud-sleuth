/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.cloud.sleuth.zipkin.stream;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.Host;
import org.springframework.cloud.sleuth.stream.Spans;

import zipkin.Constants;

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
	public void appendsServerAddressTagIfClientLogIsPresent() {
		Span span = span("foo");
		span.logEvent(Constants.CLIENT_SEND);
		Spans spans = new Spans(this.host, Collections.singletonList(span));

		List<zipkin.Span> result = ConvertToZipkinSpanList.convert(spans);

		assertThat(result)
				.hasSize(1)
				.flatExtracting(input1 -> input1.binaryAnnotations)
				.filteredOn("key", Constants.SERVER_ADDR)
				.extracting(input -> input.endpoint.serviceName)
				.contains("myservice");
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

	Span span(String name) {
		Long id = new Random().nextLong();
		return new Span(1, 3, "message:" + name, id, Collections.<Long>emptyList(), id, true, true,
				"process");
	}
}