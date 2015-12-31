package org.springframework.cloud.sleuth.instrument.web;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.common.MvcITest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(TraceFilterIntegartionTests.class)
@DefaultTestAutoConfiguration
public class TraceFilterIntegartionTests extends MvcITest {

	@Autowired
	TraceManager traceManager;

	@Test
	public void should_create_and_return_trace_in_HTTP_header() throws Exception {
		MvcResult mvcResult = whenSentPingWithoutTracingData();

		then(tracingHeaderFrom(mvcResult)).isNotNull().isNotEmpty();
	}

	@Test
	public void when_correlationId_is_sent_should_not_create_a_new_one_but_return_the_existing_one_instead()
			throws Exception {
		String expectedTraceId = "passedCorId";

		MvcResult mvcResult = whenSentPingWithTraceId(expectedTraceId);

		then(tracingHeaderFrom(mvcResult)).isEqualTo(expectedTraceId);
	}

	@Override
	protected void configureMockMvcBuilder(DefaultMockMvcBuilder mockMvcBuilder) {
		mockMvcBuilder.addFilters(new TraceFilter(this.traceManager));
	}

	private MvcResult whenSentPingWithoutTracingData() throws Exception {
		return this.mockMvc
				.perform(MockMvcRequestBuilders.get("/ping").accept(MediaType.TEXT_PLAIN))
				.andReturn();
	}

	private MvcResult whenSentPingWithTraceId(String passedCorrelationId)
			throws Exception {
		return sendPingWithTraceId(Trace.TRACE_ID_NAME, passedCorrelationId);
	}

	private MvcResult sendPingWithTraceId(String headerName, String passedCorrelationId)
			throws Exception {
		return this.mockMvc
				.perform(MockMvcRequestBuilders.get("/ping").accept(MediaType.TEXT_PLAIN)
						.header(headerName, passedCorrelationId)
						.header(Trace.SPAN_ID_NAME, UUID.randomUUID().toString()))
				.andReturn();
	}

	private String tracingHeaderFrom(MvcResult mvcResult) {
		return mvcResult.getResponse().getHeader(Trace.TRACE_ID_NAME);
	}
}
