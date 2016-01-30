package org.springframework.cloud.sleuth.instrument.web;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.cloud.sleuth.instrument.web.common.AbstractMvcIntegrationTest;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(TraceFilterAlwaysSamplerIntegrationTests.class)
@DefaultTestAutoConfiguration
@RestController
@Configuration
@Import(AlwaysSampler.class)
public class TraceFilterAlwaysSamplerIntegrationTests extends AbstractMvcIntegrationTest {

	private static Log logger = LogFactory
			.getLog(TraceFilterAlwaysSamplerIntegrationTests.class);

	@Autowired
	Tracer tracer;
	@Autowired
	TraceKeys traceKeys;

	static Span span;

	@RequestMapping("/ping")
	public String ping() {
		logger.info("ping");
		span = this.tracer.getCurrentSpan();
		return "ping";
	}

	@Test
	public void when_always_sampler_is_used_span_is_exportable() throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentPingWithTraceId(expectedTraceId);

		then(tracingHeaderFrom(mvcResult)).isEqualTo(expectedTraceId);
		then(span.isExportable());
	}

	@Test
	public void when_not_sampling_header_present_span_is_not_exportable() throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentPingWithTraceIdAndNotSampling(expectedTraceId);

		then(tracingHeaderFrom(mvcResult)).isEqualTo(expectedTraceId);
		then(span.isExportable()).isFalse();
	}

	@Override
	protected void configureMockMvcBuilder(DefaultMockMvcBuilder mockMvcBuilder) {
		mockMvcBuilder.addFilters(new TraceFilter(this.tracer, this.traceKeys));
	}

	private MvcResult whenSentPingWithTraceIdAndNotSampling(Long traceId)
			throws Exception {
		return sendPingWithTraceId(Span.TRACE_ID_NAME, traceId, false);
	}

	private MvcResult whenSentPingWithTraceId(Long traceId) throws Exception {
		return sendPingWithTraceId(Span.TRACE_ID_NAME, traceId);
	}

	private MvcResult sendPingWithTraceId(String headerName, Long correlationId)
			throws Exception {
		return sendPingWithTraceId(headerName, correlationId, true);
	}

	private MvcResult sendPingWithTraceId(String headerName, Long correlationId,
			boolean sampling) throws Exception {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.get("/ping")
				.accept(MediaType.TEXT_PLAIN)
				.header(headerName, Span.toHex(correlationId))
				.header(Span.SPAN_ID_NAME, Span.toHex(new Random().nextLong()));
		if (!sampling) {
			request.header(Span.NOT_SAMPLED_NAME, "true");
		}
		return this.mockMvc.perform(request).andReturn();
	}

	private Long tracingHeaderFrom(MvcResult mvcResult) {
		return Span.fromHex(mvcResult.getResponse().getHeader(Span.TRACE_ID_NAME));
	}
}
