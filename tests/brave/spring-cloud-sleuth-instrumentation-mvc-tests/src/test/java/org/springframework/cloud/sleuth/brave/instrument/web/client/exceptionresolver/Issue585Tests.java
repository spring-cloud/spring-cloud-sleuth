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

package org.springframework.cloud.sleuth.brave.instrument.web.client.exceptionresolver;

import java.time.Instant;

import javax.servlet.http.HttpServletRequest;

import brave.Span;
import brave.Tracing;
import brave.handler.SpanHandler;
import brave.propagation.CurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(classes = TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class Issue585Tests {

	TestRestTemplate testRestTemplate = new TestRestTemplate();

	@Autowired
	CurrentTraceContext currentTraceContext;

	@Autowired
	TestSpanHandler spans;

	@LocalServerPort
	int port;

	@Test
	public void should_report_span_when_using_custom_exception_resolver() {
		ResponseEntity<String> entity = this.testRestTemplate
				.getForEntity("http://localhost:" + this.port + "/sleuthtest?greeting=foo", String.class);

		Awaitility.await().untilAsserted(() -> {
			then(this.currentTraceContext.get()).isNull();
			then(entity.getStatusCode().value()).isEqualTo(500);
			then(this.spans.get(0).tags()).containsEntry("custom", "tag").containsKeys("error");
		});
	}

}

@SpringBootApplication
class TestConfig {

	@Bean
	SpanHandler testSpanHandler() {
		return new TestSpanHandler();
	}

	@Bean
	Sampler testSampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

}

@RestController
class TestController {

	private final static Logger logger = LoggerFactory.getLogger(TestController.class);

	@RequestMapping(value = "sleuthtest", method = RequestMethod.GET)
	public ResponseEntity<String> testSleuth(@RequestParam String greeting) {
		if (greeting.equalsIgnoreCase("hello")) {
			return new ResponseEntity<>("Hello World", HttpStatus.OK);
		}
		else {
			throw new RuntimeException("This is a test error");
		}
	}

}

@ControllerAdvice
class CustomExceptionHandler extends ResponseEntityExceptionHandler {

	private final static Logger logger = LoggerFactory.getLogger(CustomExceptionHandler.class);

	@Autowired
	private Tracing tracer;

	@ExceptionHandler(Exception.class)
	protected ResponseEntity<ExceptionResponse> handleDefaultError(Exception ex, HttpServletRequest request) {
		ExceptionResponse exceptionResponse = new ExceptionResponse("ERR-01", ex.getMessage(),
				HttpStatus.INTERNAL_SERVER_ERROR, request.getRequestURI(), Instant.now().toEpochMilli());
		reportErrorSpan(ex.getMessage());
		return new ResponseEntity<>(exceptionResponse, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private void reportErrorSpan(String message) {
		Span span = this.tracer.tracer().currentSpan();
		span.annotate("ERROR: " + message);
		span.tag("custom", "tag");
		logger.info("Foo");
	}

}

@JsonInclude(JsonInclude.Include.NON_NULL)
class ExceptionResponse {

	private String errorCode;

	private String errorMessage;

	private HttpStatus httpStatus;

	private String path;

	private Long epochTime;

	ExceptionResponse(String errorCode, String errorMessage, HttpStatus httpStatus, String path, Long epochTime) {
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
		this.httpStatus = httpStatus;
		this.path = path;
		this.epochTime = epochTime;
	}

	public String getErrorCode() {
		return this.errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorMessage() {
		return this.errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public HttpStatus getHttpStatus() {
		return this.httpStatus;
	}

	public void setHttpStatus(HttpStatus httpStatus) {
		this.httpStatus = httpStatus;
	}

	public String getPath() {
		return this.path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Long getEpochTime() {
		return this.epochTime;
	}

	public void setEpochTime(Long epochTime) {
		this.epochTime = epochTime;
	}

}
