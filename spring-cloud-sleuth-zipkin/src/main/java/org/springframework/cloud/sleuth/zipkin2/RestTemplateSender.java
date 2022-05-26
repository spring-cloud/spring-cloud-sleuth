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

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.reporter.Sender;

import java.net.URI;

/**
 * {@link Sender} that uses {@link RestTemplate} to send spans to Zipkin.
 *
 * @since 3.0.0
 */
public class RestTemplateSender extends HttpSender {
	@Deprecated
	public RestTemplateSender(RestTemplate restTemplate, String baseUrl, BytesEncoder<Span> encoder) {
		this(restTemplate, baseUrl, "", encoder);
	}

	public RestTemplateSender(RestTemplate restTemplate, String baseUrl, String apiPath, BytesEncoder<Span> encoder) {
		super((url, mediaType, bytes) -> post(url, mediaType, bytes, restTemplate), baseUrl, apiPath, encoder);
	}

	private static void post(String url, MediaType mediaType, byte[] json, RestTemplate restTemplate) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(mediaType);
		RequestEntity<byte[]> requestEntity = new RequestEntity<>(json, httpHeaders, HttpMethod.POST, URI.create(url));
		restTemplate.exchange(requestEntity, String.class);
	}

	@Override
	public String toString() {
		return "RestTemplateSender{" + url + "}";
	}

}
