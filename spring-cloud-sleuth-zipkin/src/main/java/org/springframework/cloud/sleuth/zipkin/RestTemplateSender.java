package org.springframework.cloud.sleuth.zipkin;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import zipkin.reporter.BytesMessageEncoder;
import zipkin.reporter.Callback;
import zipkin.reporter.Encoding;
import zipkin.reporter.Sender;

final class RestTemplateSender implements Sender {
	final RestTemplate restTemplate;
	final String url;

	RestTemplateSender(RestTemplate restTemplate, String baseUrl) {
		this.restTemplate = restTemplate;
		this.url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v1/spans";
	}

	@Override public Encoding encoding() {
		return Encoding.JSON;
	}

	@Override public int messageMaxBytes() {
		// This will drop a span larger than 5MiB. Note: values like 512KiB benchmark better.
		return 5 * 1024 * 1024;
	}

	@Override public int messageSizeInBytes(List<byte[]> spans) {
		return encoding().listSizeInBytes(spans);
	}

	/** close is typically called from a different thread */
	transient boolean closeCalled;

	@Override public void sendSpans(List<byte[]> encodedSpans, Callback callback) {
		if (this.closeCalled) throw new IllegalStateException("close");
		try {
			byte[] message = BytesMessageEncoder.JSON.encode(encodedSpans);
			post(message);
			callback.onComplete();
		} catch (Throwable e) {
			callback.onError(e);
			if (e instanceof Error) throw (Error) e;
		}
	}

	/** Sends an empty json message to the configured endpoint. */
	@Override public CheckResult check() {
		try {
			post(new byte[] {'[', ']'});
			return CheckResult.OK;
		} catch (Exception e) {
			return CheckResult.failed(e);
		}
	}

	@Override public void close() {
		this.closeCalled = true;
	}

	void post(byte[] json) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		RequestEntity<byte[]> requestEntity =
				new RequestEntity<>(json, httpHeaders, HttpMethod.POST, URI.create(this.url));
		this.restTemplate.exchange(requestEntity, String.class);
	}
}
