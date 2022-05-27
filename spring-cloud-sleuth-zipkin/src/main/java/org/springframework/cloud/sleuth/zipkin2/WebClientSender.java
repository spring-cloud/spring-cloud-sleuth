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
import java.time.Duration;
import java.util.function.Function;

import reactor.core.publisher.Mono;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.reporter.Sender;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link Sender} that uses {@link WebClient} to send spans to Zipkin.
 *
 * @since 3.1.0
 */
public class WebClientSender extends HttpSender {

	private static final long DEFAULT_CHECK_TIMEOUT = 1_000L;

	/**
	 * Use
	 * {@link WebClientSender#WebClientSender(Function, WebClient, String, String, BytesEncoder, long)}.
	 * @param webClient web client
	 * @param baseUrl base url
	 * @param apiPath api path
	 * @param encoder encoder
	 * @deprecated use
	 * {@link WebClientSender#WebClientSender(Function, WebClient, String, String, BytesEncoder, long)}
	 */
	@Deprecated
	public WebClientSender(WebClient webClient, String baseUrl, String apiPath, BytesEncoder<Span> encoder) {
		this(null, webClient, baseUrl, apiPath, encoder, DEFAULT_CHECK_TIMEOUT);
	}

	/**
	 * Creates a new instance of {@link WebClientSender}.
	 * @param webClient web client
	 * @param baseUrl base url
	 * @param apiPath api path
	 * @param encoder encoder
	 * @param checkTimeout check timeout
	 */
	public WebClientSender(WebClient webClient, String baseUrl, String apiPath, BytesEncoder<Span> encoder,
			long checkTimeout) {
		super((url, mediaType, bytes) -> post(null, url, mediaType, bytes, webClient, checkTimeout).block(), baseUrl,
				apiPath, encoder);
	}

	/**
	 * Creates a new instance of {@link WebClientSender}.
	 * @param webClient web client
	 * @param wrapperFunction function that will be run on onErrorResume. Send in null to
	 * get default behavior.
	 * @param baseUrl base url
	 * @param apiPath api path
	 * @param encoder encoder
	 * @param checkTimeout check timeout
	 */
	public WebClientSender(Function<Mono<ResponseEntity<Void>>, Mono<ResponseEntity<Void>>> wrapperFunction,
			WebClient webClient, String baseUrl, String apiPath, BytesEncoder<Span> encoder, long checkTimeout) {
		super((url, mediaType, bytes) -> post(wrapperFunction, url, mediaType, bytes, webClient, checkTimeout).block(),
				baseUrl, apiPath, encoder);
	}

	private static Mono<ResponseEntity<Void>> post(
			Function<Mono<ResponseEntity<Void>>, Mono<ResponseEntity<Void>>> wrapperFunction, String url,
			MediaType mediaType, byte[] json, WebClient webClient, long checkTimeout) {
		if (wrapperFunction == null) {
			wrapperFunction = (response) -> response;
		}

		return wrapperFunction.apply(webClient.post().uri(URI.create(url)).accept(mediaType).contentType(mediaType)
				.bodyValue(json).retrieve().toBodilessEntity().timeout(Duration.ofMillis(checkTimeout)));
	}

	@Override
	public String toString() {
		return "WebClientSender{" + url + "}";
	}

}
