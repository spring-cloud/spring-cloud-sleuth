/*
 * Copyright 2013-2015 the original author or authors.
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
package tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.Span;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Random;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Runnable that will send a request via the provide rest template to the
 * given url. It will also append the provided TraceID as the request's header
 *
 * @author Marcin Grzejszczak
 */
@Slf4j
public class RequestSendingRunnable implements Runnable {
	private final RestTemplate restTemplate;
	private final String url;
	private final long traceId;
	private final Random random = new Random();
	private final long spanId;

	public RequestSendingRunnable(RestTemplate restTemplate, String url, long traceId,
			Long spanId) {
		this.restTemplate = restTemplate;
		this.url = url;
		this.traceId = traceId;
		this.spanId = spanId != null ? spanId : this.random.nextLong();
	}

	@Override
	public void run() {
		log.info("Sending the request to url [{}] with trace id in headers [{}]", this.url, this.traceId);
		ResponseEntity<String> responseEntity =
				this.restTemplate.exchange(requestWithTraceId(), String.class);
		then(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
		log.info("Received the following response [{}]", responseEntity);
	}

	private RequestEntity requestWithTraceId() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(Span.TRACE_ID_NAME, Span.toHex(this.traceId));
		headers.add(Span.SPAN_ID_NAME, Span.toHex(this.spanId));
		URI uri = URI.create(this.url);
		RequestEntity requestEntity = new RequestEntity<>(headers, HttpMethod.GET, uri);
		log.info("Request [" + requestEntity + "] is ready");
		return requestEntity;
	}
}
