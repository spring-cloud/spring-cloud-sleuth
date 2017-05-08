package org.springframework.cloud.sleuth.instrument.web.client.exceptionresolver;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import javax.servlet.http.HttpServletRequest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.ListOfSpans;
import org.springframework.cloud.sleuth.assertions.SleuthAssertions;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class Issue585Tests {

	TestRestTemplate testRestTemplate = new TestRestTemplate();
	@Autowired ArrayListSpanAccumulator accumulator;
	@LocalServerPort int port;

	@Test
	public void should_report_span_when_using_custom_exception_resolver() {
		ResponseEntity<String> entity = this.testRestTemplate.getForEntity(
				"http://localhost:" + this.port + "/sleuthtest?greeting=foo",
				String.class);

		then(entity.getStatusCode().value()).isEqualTo(500);
		then(new ListOfSpans(this.accumulator.getSpans()))
				.hasASpanWithTagEqualTo("custom", "tag")
				.hasASpanWithTagKeyEqualTo("error");
	}
}

@SpringBootApplication
class TestConfig {

	@Bean SpanReporter testSpanReporter() {
		return new ArrayListSpanAccumulator();
	}

	@Bean Sampler testSampler() {
		return new AlwaysSampler();
	}
}

@RestController
class TestController {

	private final static Logger logger = LoggerFactory.getLogger(TestController.class);

	@RequestMapping(value = "sleuthtest", method = RequestMethod.GET)
	public ResponseEntity<String> testSleuth(@RequestParam String greeting) {
		if (greeting.equalsIgnoreCase("hello")) {
			return new ResponseEntity<>("Hello World", HttpStatus.OK);
		} else {
			throw new RuntimeException("This is a test error");
		}
	}
}

@ControllerAdvice
class CustomExceptionHandler extends ResponseEntityExceptionHandler {

	private final static Logger logger = LoggerFactory
			.getLogger(CustomExceptionHandler.class);

	@Autowired private Tracer tracer;

	@ExceptionHandler(value = { Exception.class })
	protected ResponseEntity<ExceptionResponse> handleDefaultError(
			Exception ex, HttpServletRequest request) {
		ExceptionResponse exceptionResponse = new ExceptionResponse("ERR-01",
				ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
				request.getRequestURI(), Instant.now().toEpochMilli());
		reportErrorSpan(ex.getMessage());
		return new ResponseEntity<>(exceptionResponse, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private void reportErrorSpan(String message) {
		Span span = tracer.getCurrentSpan();
		span.logEvent("ERROR: " + message);
		tracer.addTag("custom", "tag");
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

	ExceptionResponse(String errorCode, String errorMessage, HttpStatus httpStatus,
			String path, Long epochTime) {
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
		this.httpStatus = httpStatus;
		this.path = path;
		this.epochTime = epochTime;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public void setHttpStatus(HttpStatus httpStatus) {
		this.httpStatus = httpStatus;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Long getEpochTime() {
		return epochTime;
	}

	public void setEpochTime(Long epochTime) {
		this.epochTime = epochTime;
	}

}