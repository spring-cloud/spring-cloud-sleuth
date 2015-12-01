package org.springframework.cloud.sleuth.instrument.web;

import static org.assertj.core.api.BDDAssertions.then;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.IdGenerator;
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
@SpringApplicationConfiguration(TraceFilterITest.class)
@DefaultTestAutoConfiguration
public class TraceFilterITest extends MvcITest {

	private static final String PING_ENDPOINT = "/ping";

	@Autowired TraceManager traceManager;
	@Autowired IdGenerator idGenerator;

	@Test
	public void should_create_and_return_trace_in_HTTP_header() throws Exception {
		MvcResult mvcResult = whenSentPingWithoutTracingData();

		then(tracingHeaderFrom(mvcResult)).isNotNull().isNotEmpty();
	}

	@Test
	public void when_correlationId_is_sent_should_not_create_a_new_one_but_return_the_existing_one_instead() throws Exception {
		String expectedTraceId = "passedCorId";

		MvcResult mvcResult = whenSentPingWithTraceId(expectedTraceId);

		then(tracingHeaderFrom(mvcResult)).isEqualTo(expectedTraceId);
	}

	@Override
	protected void configureMockMvcBuilder(DefaultMockMvcBuilder mockMvcBuilder) {
		mockMvcBuilder.addFilters(new TraceFilter(traceManager, idGenerator));
	}

	private MvcResult whenSentPingWithoutTracingData() throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.get(PING_ENDPOINT).accept(MediaType.TEXT_PLAIN)).andReturn();
	}

	private MvcResult whenSentPingWithTraceId(String passedCorrelationId) throws Exception {
		return sendPingWithTraceId(Trace.TRACE_ID_NAME, passedCorrelationId);
	}

	private MvcResult sendPingWithTraceId(String headerName, String passedCorrelationId) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.get(PING_ENDPOINT).accept(MediaType.TEXT_PLAIN)
				.header(headerName, passedCorrelationId)).andReturn();
	}

	private String tracingHeaderFrom(MvcResult mvcResult) {
		return mvcResult.getResponse().getHeader(Trace.TRACE_ID_NAME);
	}
}
