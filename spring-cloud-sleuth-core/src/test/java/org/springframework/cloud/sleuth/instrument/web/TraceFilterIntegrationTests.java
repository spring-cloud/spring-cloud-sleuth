package org.springframework.cloud.sleuth.instrument.web;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.ManagementServerProperties;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.common.AbstractMvcIntegrationTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(TraceFilterIntegrationTests.class)
@DefaultTestAutoConfiguration
@RestController
public class TraceFilterIntegrationTests extends AbstractMvcIntegrationTest {

	private static Log logger = LogFactory.getLog(TraceFilterIntegrationTests.class);

	@Autowired Tracer tracer;
	@Autowired TraceKeys traceKeys;
	@Autowired TraceFilter traceFilter;

	static Span span;

	@RequestMapping("/ping")
	public String ping() {
		logger.info("ping");
		span = this.tracer.getCurrentSpan();
		return "ping";
	}

	@RequestMapping("/future")
	public CompletableFuture<String> future() {
		logger.info("future");
		return CompletableFuture.completedFuture("ping");
	}

	@Test
	public void should_create_and_return_trace_in_HTTP_header() throws Exception {
		MvcResult mvcResult = whenSentPingWithoutTracingData();

		then(tracingHeaderFrom(mvcResult)).isNotNull();
		then(TraceFilterIntegrationTests.span).hasLoggedAnEvent(Span.SERVER_RECV).hasLoggedAnEvent(Span.SERVER_SEND);
	}

	@Test
	public void should_ignore_sampling_the_span_if_uri_matches_management_properties_context_path() throws Exception {
		MvcResult mvcResult = whenSentInfoWithTraceId(new Random().nextLong());

		then(notSampledHeaderIsPresent(mvcResult)).isEqualTo(true);
	}

	@Test
	public void when_traceId_is_sent_should_not_create_a_new_one_but_return_the_existing_one_instead()
			throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentPingWithTraceId(expectedTraceId);

		then(tracingHeaderFrom(mvcResult)).isEqualTo(expectedTraceId);
	}

	@Test
	public void when_message_is_sent_should_eventually_clear_mdc()
			throws Exception {
		Long expectedTraceId = new Random().nextLong();

		whenSentPingWithTraceId(expectedTraceId);

		then(MDC.getCopyOfContextMap()).isEmpty();
	}

	@Test
	public void when_traceId_is_sent_to_async_endpoint_span_is_joined()
			throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentFutureWithTraceId(expectedTraceId);
		mvcResult = this.mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk())
				.andReturn();

		then(tracingHeaderFrom(mvcResult)).isEqualTo(expectedTraceId);
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
		return sendPingWithTraceId("/additionalContextPath/info", Span.TRACE_ID_NAME, passedTraceId);
	}

	private MvcResult whenSentFutureWithTraceId(Long passedTraceId) throws Exception {
		return sendPingWithTraceId("/future", Span.TRACE_ID_NAME, passedTraceId);
	}

	private MvcResult sendPingWithTraceId(String headerName, Long traceId)
			throws Exception {
		return sendPingWithTraceId("/ping", headerName, traceId);
	}

	private MvcResult sendPingWithTraceId(String path, String headerName,
			Long traceId) throws Exception {
		return this.mockMvc
				.perform(MockMvcRequestBuilders.get(path).accept(MediaType.TEXT_PLAIN)
						.header(headerName, Span.idToHex(traceId))
						.header(Span.SPAN_ID_NAME, Span.idToHex(new Random().nextLong())))
				.andReturn();
	}

	private Long tracingHeaderFrom(MvcResult mvcResult) {
		return Span.hexToId(mvcResult.getResponse().getHeader(Span.TRACE_ID_NAME));
	}

	private boolean notSampledHeaderIsPresent(MvcResult mvcResult) {
		return Span.SPAN_NOT_SAMPLED.equals(mvcResult.getResponse().getHeader(Span.SAMPLED_NAME));
	}

	@Configuration
	static class Config {
		@Bean
		ManagementServerProperties managementServerProperties() {
			ManagementServerProperties managementServerProperties = new ManagementServerProperties();
			managementServerProperties.setContextPath("/additionalContextPath");
			return managementServerProperties;
		}
	}
}
