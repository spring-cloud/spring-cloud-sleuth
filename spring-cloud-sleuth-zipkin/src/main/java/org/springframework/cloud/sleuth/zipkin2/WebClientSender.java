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

import java.net.URI;

import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.reporter.Sender;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link Sender} that uses {@link WebClient} to send spans to Zipkin.
 *
 * @since 3.1.0
 */
public class WebClientSender extends HttpSender {

	public WebClientSender(WebClient webClient, String baseUrl, String apiPath, BytesEncoder<Span> encoder) {
		super((url, mediaType, bytes) -> post(url, mediaType, bytes, webClient), baseUrl, apiPath, encoder);
	}

	private static void post(String url, MediaType mediaType, byte[] json, WebClient webClient) {
		webClient.post().uri(URI.create(url)).accept(mediaType).contentType(mediaType).bodyValue(json).retrieve()
				.toBodilessEntity().subscribe();
	}

	@Override
	public String toString() {
		return "WebClientSender{" + url + "}";
	}

}
