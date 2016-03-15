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

import java.net.URI;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Runnable that will send a request via the provide rest template to the
 * given url. It will also append the provided TraceID as the request's header
 *
 * @author Marcin Grzejszczak
 */
public class RequestSendingRunnable implements Runnable {

	private static final Log log = LogFactory.getLog(RequestSendingRunnable.class);

	private final RestTemplate restTemplate;
	private final TraceHeaders traceHeaders;
	private final String url;
	private final long traceId;
	private final long spanId;

	public RequestSendingRunnable(RestTemplate restTemplate, TraceHeaders traceHeaders,
			long traceId, Long spanId, String url) {
		this.restTemplate = restTemplate;
		this.traceHeaders = traceHeaders;
		this.url = url;
		this.traceId = traceId;
		this.spanId = spanId != null ? spanId : new Random().nextLong();
	}

	@Override
	public void run() {
		log.info(String.format("Sending the request to url [%s] with trace id in headers [%d]", this.url, this.traceId));
		ResponseEntity<String> responseEntity =
				this.restTemplate.exchange(requestWithTraceId(), String.class);
		then(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
		log.info(String.format("Received the following response [%s]", responseEntity));
	}

	private RequestEntity<Void> requestWithTraceId() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(this.traceHeaders.getTraceId(), Span.idToHex(this.traceId));
		headers.add(this.traceHeaders.getSpanId(), Span.idToHex(this.spanId));
		URI uri = URI.create(this.url);
		RequestEntity<Void> requestEntity = new RequestEntity<>(headers, HttpMethod.GET, uri);
		log.info("Request [" + requestEntity + "] is ready");
		return requestEntity;
	}
}
