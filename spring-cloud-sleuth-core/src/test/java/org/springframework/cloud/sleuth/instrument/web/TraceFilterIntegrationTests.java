package org.springframework.cloud.sleuth.instrument.web;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.cloud.sleuth.instrument.web.common.AbstractMvcIntegrationTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(TraceFilterIntegrationTests.class)
@DefaultTestAutoConfiguration
public class TraceFilterIntegrationTests extends AbstractMvcIntegrationTest {

	@Autowired
	Tracer tracer;
	@Autowired
	TraceKeys traceKeys;

	@Test
	public void should_create_and_return_trace_in_HTTP_header() throws Exception {
		MvcResult mvcResult = whenSentPingWithoutTracingData();

		then(tracingHeaderFrom(mvcResult)).isNotNull();
	}

	@Test
	public void when_correlationId_is_sent_should_not_create_a_new_one_but_return_the_existing_one_instead()
			throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentPingWithTraceId(expectedTraceId);

		then(tracingHeaderFrom(mvcResult)).isEqualTo(expectedTraceId);
	}

	@Override
	protected void configureMockMvcBuilder(DefaultMockMvcBuilder mockMvcBuilder) {
		mockMvcBuilder.addFilters(new TraceFilter(this.tracer, this.traceKeys));
	}

	private MvcResult whenSentPingWithoutTracingData() throws Exception {
		return this.mockMvc
				.perform(MockMvcRequestBuilders.get("/ping").accept(MediaType.TEXT_PLAIN))
				.andReturn();
	}

	private MvcResult whenSentPingWithTraceId(Long passedTraceId)
			throws Exception {
		return sendPingWithTraceId(Span.TRACE_ID_NAME, passedTraceId);
	}

	private MvcResult sendPingWithTraceId(String headerName, Long passedCorrelationId)
			throws Exception {
		return this.mockMvc
				.perform(MockMvcRequestBuilders.get("/ping").accept(MediaType.TEXT_PLAIN)
						.header(headerName, Span.IdConverter.toHex(passedCorrelationId))
						.header(Span.SPAN_ID_NAME, Span.IdConverter.toHex(new Random().nextLong())))
				.andReturn();
	}

	private Long tracingHeaderFrom(MvcResult mvcResult) {
		return Span.IdConverter.fromHex(mvcResult.getResponse().getHeader(Span.TRACE_ID_NAME));
	}
}
