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
import org.springframework.cloud.sleuth.Trace;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

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

	public RequestSendingRunnable(RestTemplate restTemplate, String url, long traceId) {
		this.restTemplate = restTemplate;
		this.url = url;
		this.traceId = traceId;
	}

	@Override
	public void run() {
		log.info("Sending the request to url [{}] with trace id in headers [{}]", url, traceId);
		ResponseEntity<String> responseEntity = restTemplate.exchange(requestWithTraceId(traceId), String.class);
		then(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
		log.info("Received the following response [{}]", responseEntity);
	}

	private RequestEntity requestWithTraceId(long traceId) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(Trace.TRACE_ID_NAME, Span.Converter.toHexString(traceId));
		URI uri = URI.create(url);
		RequestEntity requestEntity = new RequestEntity<>(headers, HttpMethod.GET, uri);
		log.info("Request [" + requestEntity + "] is ready");
		return requestEntity;
	}
}
