package org.springframework.cloud.sleuth.instrument.web;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.ManagementServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.common.AbstractMvcIntegrationTest;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TraceFilterIntegrationTests.Config.class)
public class TraceFilterIntegrationTests extends AbstractMvcIntegrationTest {

	private static Log logger = LogFactory.getLog(TraceFilterIntegrationTests.class);

	@Autowired TraceFilter traceFilter;
	@Autowired ArrayListSpanAccumulator spanAccumulator;

	private static Span span;

	@Before
	@After
	public void clearSpans() {
		this.spanAccumulator.getSpans().clear();
	}

	@Test
	public void should_create_and_return_trace_in_HTTP_header() throws Exception {
		whenSentPingWithoutTracingData();

		then(this.spanAccumulator.getSpans()).hasSize(1);
		Span span = this.spanAccumulator.getSpans().get(0);
		then(span).hasLoggedAnEvent(Span.SERVER_RECV)
				.hasATagWithKey(new TraceKeys().getMvc().getControllerClass())
				.hasATagWithKey(new TraceKeys().getMvc().getControllerMethod())
				.hasLoggedAnEvent(Span.SERVER_SEND);
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void should_ignore_sampling_the_span_if_uri_matches_management_properties_context_path()
			throws Exception {
		MvcResult mvcResult = whenSentInfoWithTraceId(new Random().nextLong());

		// https://github.com/spring-cloud/spring-cloud-sleuth/issues/327
		// we don't want to respond with any tracing data
		then(notSampledHeaderIsPresent(mvcResult)).isEqualTo(false);
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void when_traceId_is_sent_should_not_create_a_new_one_but_return_the_existing_one_instead()
			throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentPingWithTraceId(expectedTraceId);

		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void when_message_is_sent_should_eventually_clear_mdc() throws Exception {
		Long expectedTraceId = new Random().nextLong();

		whenSentPingWithTraceId(expectedTraceId);

		then(MDC.getCopyOfContextMap()).isEmpty();
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void when_traceId_is_sent_to_async_endpoint_span_is_joined() throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentFutureWithTraceId(expectedTraceId);
		this.mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().isOk()).andReturn();

		then(this.tracer.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void should_add_a_custom_tag_to_the_span_created_in_controller() throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentDeferredWithTraceId(expectedTraceId);
		this.mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().isOk()).andReturn();

		Optional<Span> taggedSpan = this.spanAccumulator.getSpans().stream()
				.filter(span -> span.tags().containsKey("tag")).findFirst();
		then(taggedSpan.isPresent()).isTrue();
		then(taggedSpan.get()).hasATag("tag", "value");
		then(taggedSpan.get()).hasATag("mvc.controller.method", "deferredMethod");
		then(taggedSpan.get()).hasATag("mvc.controller.class", "TestController");
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void should_log_tracing_information_when_exception_was_thrown() throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentToNonExistentEndpointWithTraceId(expectedTraceId);

		then(this.tracer.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void should_assume_that_a_request_without_span_and_with_trace_is_a_root_span() throws Exception {
		Long expectedTraceId = new Random().nextLong();

		whenSentRequestWithTraceIdAndNoSpanId(expectedTraceId);
		whenSentRequestWithTraceIdAndNoSpanId(expectedTraceId);

		then(this.spanAccumulator.getSpans().stream().filter(span ->
				span.getSpanId() == span.getTraceId()).findAny().isPresent()).as("a root span exists").isTrue();
		then(this.tracer.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Override
	protected void configureMockMvcBuilder(DefaultMockMvcBuilder mockMvcBuilder) {
		mockMvcBuilder.addFilters(this.traceFilter);
	}

	private MvcResult whenSentPingWithoutTracingData() throws Exception {
		return this.mockMvc
				.perform(MockMvcRequestBuilders.get("/ping").accept(MediaType.TEXT_PLAIN))
				.andReturn();
	}

	private MvcResult whenSentPingWithTraceId(Long passedTraceId) throws Exception {
		return sendPingWithTraceId(Span.TRACE_ID_NAME, passedTraceId);
	}

	private MvcResult whenSentInfoWithTraceId(Long passedTraceId) throws Exception {
		return sendRequestWithTraceId("/additionalContextPath/info", Span.TRACE_ID_NAME,
				passedTraceId);
	}

	private MvcResult whenSentFutureWithTraceId(Long passedTraceId) throws Exception {
		return sendRequestWithTraceId("/future", Span.TRACE_ID_NAME, passedTraceId);
	}

	private MvcResult whenSentDeferredWithTraceId(Long passedTraceId) throws Exception {
		return sendDeferredWithTraceId(Span.TRACE_ID_NAME, passedTraceId);
	}

	private MvcResult whenSentToNonExistentEndpointWithTraceId(Long passedTraceId) throws Exception {
		return sendRequestWithTraceId("/exception/nonExistent", Span.TRACE_ID_NAME, passedTraceId, HttpStatus.NOT_FOUND);
	}

	private MvcResult sendPingWithTraceId(String headerName, Long traceId)
			throws Exception {
		return sendRequestWithTraceId("/ping", headerName, traceId);
	}

	private MvcResult sendDeferredWithTraceId(String headerName, Long traceId)
			throws Exception {
		return sendRequestWithTraceId("/deferred", headerName, traceId);
	}

	private MvcResult sendRequestWithTraceId(String path, String headerName, Long traceId)
			throws Exception {
		return this.mockMvc
				.perform(MockMvcRequestBuilders.get(path).accept(MediaType.TEXT_PLAIN)
						.header(headerName, Span.idToHex(traceId))
						.header(Span.SPAN_ID_NAME, Span.idToHex(new Random().nextLong())))
				.andReturn();
	}

	private MvcResult whenSentRequestWithTraceIdAndNoSpanId(Long traceId)
			throws Exception {
		return this.mockMvc
				.perform(MockMvcRequestBuilders.get("/ping").accept(MediaType.TEXT_PLAIN)
						.header(Span.TRACE_ID_NAME, Span.idToHex(traceId)))
				.andReturn();
	}

	private MvcResult sendRequestWithTraceId(String path, String headerName, Long traceId, HttpStatus status)
			throws Exception {
		return this.mockMvc
				.perform(MockMvcRequestBuilders.get(path).accept(MediaType.TEXT_PLAIN)
						.header(headerName, Span.idToHex(traceId))
						.header(Span.SPAN_ID_NAME, Span.idToHex(new Random().nextLong())))
				.andExpect(status().is(status.value()))
				.andReturn();
	}

	private boolean notSampledHeaderIsPresent(MvcResult mvcResult) {
		return Span.SPAN_NOT_SAMPLED
				.equals(mvcResult.getResponse().getHeader(Span.SAMPLED_NAME));
	}

	@DefaultTestAutoConfiguration
	@Configuration
	protected static class Config {

		@RestController
		public static class TestController {
			@Autowired
			private Tracer tracer;

			@RequestMapping("/ping")
			public String ping() {
				logger.info("ping");
				span = this.tracer.getCurrentSpan();
				return "ping";
			}

			@RequestMapping("/throwsException")
			public void throwsException() {
				throw new RuntimeException();
			}

			@RequestMapping("/deferred")
			public DeferredResult<String> deferredMethod() {
				logger.info("deferred");
				this.tracer.addTag("tag", "value");
				span = this.tracer.getCurrentSpan();
				DeferredResult<String> result = new DeferredResult<>();
				result.setResult("deferred");
				return result;
			}

			@RequestMapping("/future")
			public CompletableFuture<String> future() {
				logger.info("future");
				return CompletableFuture.completedFuture("ping");
			}
		}

		@Configuration
		static class ManagementServer {
			@Bean
			ManagementServerProperties managementServerProperties() {
				ManagementServerProperties managementServerProperties = new ManagementServerProperties();
				managementServerProperties.setContextPath("/additionalContextPath");
				return managementServerProperties;
			}
		}

		@Bean
		public SpanReporter testSpanReporter() {
			return new ArrayListSpanAccumulator();
		}

		@Bean
		Sampler alwaysSampler() {
			return new AlwaysSampler();
		}
	}
}
