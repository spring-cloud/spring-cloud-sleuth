package org.springframework.cloud.sleuth.instrument.web;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.common.AbstractMvcIntegrationTest;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TraceFilterAlwaysSamplerIntegrationTests.Config.class)
public class TraceFilterAlwaysSamplerIntegrationTests extends AbstractMvcIntegrationTest {

	private static Log logger = LogFactory
			.getLog(TraceFilterAlwaysSamplerIntegrationTests.class);

	static Span span;

	@Test
	public void when_always_sampler_is_used_span_is_exportable() throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentPingWithTraceId(expectedTraceId);

		then(span.isExportable());
	}

	@Test
	public void when_not_sampling_header_present_span_is_not_exportable() throws Exception {
		Long expectedTraceId = new Random().nextLong();

		MvcResult mvcResult = whenSentPingWithTraceIdAndNotSampling(expectedTraceId);

		then(span.isExportable()).isFalse();
	}

	@Override
	protected void configureMockMvcBuilder(DefaultMockMvcBuilder mockMvcBuilder) {
		mockMvcBuilder.addFilters(new TraceFilter(this.tracer, this.traceKeys,
				new NoOpSpanReporter(), this.spanExtractor,
				this.httpTraceKeysInjector));
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
				.header(headerName, Span.idToHex(correlationId))
				.header(Span.SPAN_ID_NAME, Span.idToHex(new Random().nextLong()));
		request.header(Span.SAMPLED_NAME, sampling ? Span.SPAN_SAMPLED : Span.SPAN_NOT_SAMPLED);
		return this.mockMvc.perform(request).andReturn();
	}

	@DefaultTestAutoConfiguration
	@RestController
	@Configuration
	@Import(AlwaysSampler.class)
	static class Config {

		@Autowired
		private Tracer tracer;

		@RequestMapping("/ping")
		public String ping() {
			logger.info("ping");
			span = this.tracer.getCurrentSpan();
			return "ping";
		}

	}

}
