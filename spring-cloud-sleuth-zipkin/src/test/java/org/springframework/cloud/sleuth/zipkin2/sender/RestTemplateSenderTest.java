/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin2.sender;

import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.web.client.RestTemplate;
import zipkin2.Call;
import zipkin2.Span;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.UTF_8;
import static zipkin2.codec.SpanBytesEncoder.JSON_V2;

public class RestTemplateSenderTest {

  @Rule public MockWebServer server = new MockWebServer();

  String endpoint = server.url("/api/v2/spans").toString();
  RestTemplateSender sender = new RestTemplateSender(new RestTemplate(), endpoint, JSON_V2);

  /** Tests that json is not manipulated as a side-effect of using rest template. */
  @Test public void jsonIsNormal() throws Exception {
    server.enqueue(new MockResponse());

    send(CLIENT_SPAN).execute();

    assertThat(server.takeRequest().getBody().readUtf8())
        .isEqualTo("[" + new String(JSON_V2.encode(CLIENT_SPAN), UTF_8) + "]");
  }

  Call<Void> send(Span... spans) {
    return sender.sendSpans(Stream.of(spans)
        .map(JSON_V2::encode)
        .collect(toList()));
  }
}
