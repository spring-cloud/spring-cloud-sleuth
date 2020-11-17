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
import java.net.URI;
import java.util.List;

import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.CheckResult;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.Encoding;
import zipkin2.reporter.BytesMessageEncoder;
import zipkin2.reporter.Sender;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import static zipkin2.codec.SpanBytesEncoder.JSON_V2;

final class RestTemplateSender extends Sender {

	final RestTemplate restTemplate;

	final String url;

	final Encoding encoding;

	final MediaType mediaType;

	final BytesMessageEncoder messageEncoder;

	/**
	 * close is typically called from a different thread.
	 */
	transient boolean closeCalled;

	RestTemplateSender(RestTemplate restTemplate, String baseUrl, BytesEncoder<Span> encoder) {
		this.restTemplate = restTemplate;
		this.encoding = encoder.encoding();
		if (encoder.equals(JSON_V2)) {
			this.mediaType = MediaType.APPLICATION_JSON;
			this.url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v2/spans";
		}
		else if (this.encoding == Encoding.PROTO3) {
			this.mediaType = MediaType.parseMediaType("application/x-protobuf");
			this.url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v2/spans";
		}
		else if (this.encoding == Encoding.JSON) {
			this.mediaType = MediaType.APPLICATION_JSON;
			this.url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v1/spans";
		}
		else {
			throw new UnsupportedOperationException("Unsupported encoding: " + this.encoding.name());
		}
		this.messageEncoder = BytesMessageEncoder.forEncoding(this.encoding);
	}

	@Override
	public Encoding encoding() {
		return this.encoding;
	}

	@Override
	public int messageMaxBytes() {
		// This will drop a span larger than 5MiB. Note: values like 512KiB benchmark
		// better.
		return 5 * 1024 * 1024;
	}

	@Override
	public int messageSizeInBytes(List<byte[]> spans) {
		return encoding().listSizeInBytes(spans);
	}

	@Override
	public Call<Void> sendSpans(List<byte[]> encodedSpans) {
		if (this.closeCalled) {
			throw new IllegalStateException("close");
		}
		return new HttpPostCall(this.messageEncoder.encode(encodedSpans));
	}

	/**
	 * Sends an empty json message to the configured endpoint.
	 */
	@Override
	public CheckResult check() {
		try {
			post(new byte[] { '[', ']' });
			return CheckResult.OK;
		}
		catch (Exception e) {
			return CheckResult.failed(e);
		}
	}

	@Override
	public void close() {
		this.closeCalled = true;
	}

	void post(byte[] json) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(this.mediaType);
		RequestEntity<byte[]> requestEntity = new RequestEntity<>(json, httpHeaders, HttpMethod.POST,
				URI.create(this.url));
		this.restTemplate.exchange(requestEntity, String.class);
	}

	@Override
	public String toString() {
		return "RestTemplateSender{" + url + "}";
	}

	class HttpPostCall extends Call.Base<Void> {

		private final byte[] message;

		HttpPostCall(byte[] message) {
			this.message = message;
		}

		@Override
		protected Void doExecute() throws IOException {
			post(this.message);
			return null;
		}

		@Override
		protected void doEnqueue(Callback<Void> callback) {
			try {
				post(this.message);
				callback.onSuccess(null);
			}
			catch (RuntimeException | Error e) {
				callback.onError(e);
			}
		}

		@Override
		public Call<Void> clone() {
			return new HttpPostCall(this.message);
		}

	}

}
