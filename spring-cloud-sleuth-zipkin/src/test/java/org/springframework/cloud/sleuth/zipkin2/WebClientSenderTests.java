/*
 * Copyright 2013-2021 the original author or authors.
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

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import zipkin2.CheckResult;
import zipkin2.reporter.Sender;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.codec.SpanBytesEncoder.JSON_V2;
import static zipkin2.codec.SpanBytesEncoder.PROTO3;

class WebClientSenderTests extends AbstractSenderTest {

	public static final int DEFAULT_CHECK_TIMEOUT = 1_000;

	@Override
	Sender jsonSender() {
		return new WebClientSender(WebClient.builder().clientConnector(new ReactorClientHttpConnector()).build(),
				this.endpoint, null, JSON_V2, DEFAULT_CHECK_TIMEOUT);
	}

	@Override
	Sender jsonSender(String mockedApiPath) {
		return new WebClientSender(WebClient.builder().clientConnector(new ReactorClientHttpConnector()).build(),
				this.endpoint, mockedApiPath, JSON_V2, DEFAULT_CHECK_TIMEOUT);
	}

	@Override
	Sender protoSender() {
		return new WebClientSender(WebClient.builder().clientConnector(new ReactorClientHttpConnector()).build(),
				this.endpoint, "", PROTO3, DEFAULT_CHECK_TIMEOUT);
	}

	@Override
	String expectedToString() {
		return "WebClientSender{" + this.endpoint + "/api/v2/spans}";
	}

	@Override
	String expectedToStringWithNonEmptyApiPath(String mockedApiPath) {
		if ("".equals(mockedApiPath)) {
			return "WebClientSender{" + this.endpoint + "}";
		}
		return "WebClientSender{" + this.endpoint + mockedApiPath + "}";
	}

	@Test
	void customFunctionToResumeAfterError() throws IOException {
		WebClientSender sender = new WebClientSender((response) -> response.onErrorResume((error) -> Mono.empty()),
				WebClient.builder().clientConnector(new ReactorClientHttpConnector()).build(), this.endpoint, "",
				PROTO3, DEFAULT_CHECK_TIMEOUT);

		this.server.shutdown();
		CheckResult result = sender.check();
		assertThat(result.ok()).isTrue();
	}

}
