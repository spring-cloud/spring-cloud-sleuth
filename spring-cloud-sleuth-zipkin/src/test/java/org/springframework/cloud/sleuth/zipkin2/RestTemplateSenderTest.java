/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin2;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zipkin2.Call;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.Encoding;
import zipkin2.codec.SpanBytesEncoder;

import org.springframework.web.client.RestTemplate;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.codec.SpanBytesEncoder.JSON_V2;
import static zipkin2.codec.SpanBytesEncoder.PROTO3;

public class RestTemplateSenderTest {

	static final Span SPAN = Span.newBuilder().traceId("7180c278b62e8f6a216a2aea45d08fc9").parentId("6b221d5bc9e6496c")
			.id("5b4185666d50f68b").name("get /backend").kind(Span.Kind.SERVER).shared(true)
			.localEndpoint(Endpoint.newBuilder().serviceName("backend").ip("192.168.99.101").port(9000).build())
			.timestamp(1472470996250000L).duration(100000L).putTag("http.method", "GET").putTag("http.path", "/backend")
			.build();

	public MockWebServer server = new MockWebServer();

	String endpoint = this.server.url("/api/v2/spans").toString();

	RestTemplateSender sender = new RestTemplateSender(new RestTemplate(), this.endpoint, JSON_V2);

	@AfterEach
	void clean() throws IOException {
		server.close();
	}

	/**
	 * Tests that json is not manipulated as a side-effect of using rest template.
	 * @throws Exception when span sending or receiving fails
	 */
	@Test
	public void jsonIsNormal() throws Exception {
		this.server.enqueue(new MockResponse());

		send(SPAN).execute();

		assertThat(this.server.takeRequest().getBody().readUtf8())
				.isEqualTo("[" + new String(JSON_V2.encode(SPAN), "UTF-8") + "]");
	}

	@Test
	public void proto3() throws Exception {
		this.server.enqueue(new MockResponse());
		this.sender = new RestTemplateSender(new RestTemplate(), this.endpoint, PROTO3);

		send(SPAN).execute();

		RecordedRequest request = this.server.takeRequest(1, TimeUnit.SECONDS);
		assertThat(request.getHeader("Content-Type")).isEqualTo("application/x-protobuf");

		// proto3 encoding of ListOfSpan is simply a repeated span entry
		assertThat(request.getBody().readByteArray()).containsExactly(SpanBytesEncoder.PROTO3.encode(SPAN));
	}

	Call<Void> send(Span... spans) {
		SpanBytesEncoder bytesEncoder = this.sender.encoding() == Encoding.JSON ? SpanBytesEncoder.JSON_V2
				: SpanBytesEncoder.PROTO3;
		return this.sender.sendSpans(Stream.of(spans).map(bytesEncoder::encode).collect(toList()));
	}

}
