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
import java.util.List;
import java.util.Objects;

import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.CheckResult;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.Encoding;
import zipkin2.reporter.BytesMessageEncoder;
import zipkin2.reporter.Sender;

import org.springframework.http.MediaType;

import static zipkin2.codec.SpanBytesEncoder.JSON_V2;

/**
 * {@link Sender} that uses an HTTP client to send spans to Zipkin.
 *
 * @since 3.1.0
 */
abstract class HttpSender extends Sender {

	final ZipkinHttpClientSender sender;

	final String url;

	final Encoding encoding;

	final MediaType mediaType;

	final BytesMessageEncoder messageEncoder;

	/**
	 * close is typically called from a different thread.
	 */
	transient boolean closeCalled;

	HttpSender(ZipkinHttpClientSender sender, String baseUrl, String apiPath, BytesEncoder<Span> encoder) {
		this.sender = sender;
		this.encoding = encoder.encoding();
		if (encoder.equals(JSON_V2)) {
			this.mediaType = MediaType.APPLICATION_JSON;
			this.url = buildUrlWithCustomPathIfNecessary(baseUrl, apiPath,
					baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v2/spans");
		}
		else if (this.encoding == Encoding.PROTO3) {
			this.mediaType = MediaType.parseMediaType("application/x-protobuf");
			this.url = buildUrlWithCustomPathIfNecessary(baseUrl, apiPath,
					baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v2/spans");
		}
		else if (this.encoding == Encoding.JSON) {
			this.mediaType = MediaType.APPLICATION_JSON;
			this.url = buildUrlWithCustomPathIfNecessary(baseUrl, apiPath,
					baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v1/spans");
		}
		else {
			throw new UnsupportedOperationException("Unsupported encoding: " + this.encoding.name());
		}
		this.messageEncoder = BytesMessageEncoder.forEncoding(this.encoding);
	}

	private String buildUrlWithCustomPathIfNecessary(final String baseUrl, final String customApiPath,
			final String defaultUrl) {
		if (Objects.nonNull(customApiPath)) {
			return baseUrl
					+ (baseUrl.endsWith("/") || customApiPath.startsWith("/") || customApiPath.isEmpty() ? "" : "/")
					+ customApiPath;
		}
		return defaultUrl;
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
		this.sender.call(this.url, this.mediaType, json);
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
