package org.springframework.cloud.sleuth.instrument.web;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
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

import static org.assertj.core.api.BDDAssertions.then;
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
	}

	@Test
	public void should_ignore_sampling_the_span_if_uri_matches_management_properties_context_path() throws Exception {
		MvcResult mvcResult = whenSentInfoWithTraceId(new Random().nextLong());

		then(notSampledHeaderIsPresent(mvcResult)).isEqualTo(true);
	}

	@Test
	public void when_correlationId_is_sent_should_not_create_a_new_one_but_return_the_existing_one_instead()
			throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentPingWithTraceId(expectedTraceId);

		then(tracingHeaderFrom(mvcResult)).isEqualTo(expectedTraceId);
	}

	@Test
	public void when_correlationId_is_sent_to_async_endpoint_span_is_joined()
			throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentFutureWithTraceId(expectedTraceId);
		mvcResult = this.mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk())
				.andReturn();

		then(tracingHeaderFrom(mvcResult)).isEqualTo(expectedTraceId);
	}

	@Override
	protected void configureMockMvcBuilder(DefaultMockMvcBuilder mockMvcBuilder) {
		mockMvcBuilder.addFilters(traceFilter);
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

	private MvcResult sendPingWithTraceId(String headerName, Long correlationId)
			throws Exception {
		return sendPingWithTraceId("/ping", headerName, correlationId);
	}

	private MvcResult sendPingWithTraceId(String path, String headerName,
			Long correlationId) throws Exception {
		return this.mockMvc
				.perform(MockMvcRequestBuilders.get(path).accept(MediaType.TEXT_PLAIN)
						.header(headerName, Span.idToHex(correlationId))
						.header(Span.SPAN_ID_NAME, Span.idToHex(new Random().nextLong())))
				.andReturn();
	}

	private Long tracingHeaderFrom(MvcResult mvcResult) {
		return Span.hexToId(mvcResult.getResponse().getHeader(Span.TRACE_ID_NAME));
	}

	private boolean notSampledHeaderIsPresent(MvcResult mvcResult) {
		return mvcResult.getResponse().containsHeader(Span.NOT_SAMPLED_NAME);
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
