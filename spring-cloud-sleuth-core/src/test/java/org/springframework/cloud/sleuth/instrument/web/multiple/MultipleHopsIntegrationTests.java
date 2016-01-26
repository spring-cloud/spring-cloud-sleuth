package org.springframework.cloud.sleuth.instrument.web.multiple;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.event.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.cloud.sleuth.instrument.web.TraceFilter;
import org.springframework.cloud.sleuth.instrument.web.common.AbstractMvcIntegrationTest;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MultipleHopsIntegrationTests.Config.class)
public class MultipleHopsIntegrationTests extends AbstractMvcIntegrationTest {

	@Autowired Tracer tracer;
	@Autowired TraceKeys traceKeys;
	@Autowired ArrayListSpanAccumulator arrayListSpanAccumulator;

	@Override
	protected void configureMockMvcBuilder(DefaultMockMvcBuilder mockMvcBuilder) {
		mockMvcBuilder.addFilters(new TraceFilter(this.tracer, this.traceKeys));
	}

	@Test
	public void should_prepare_spans_for_export() throws Exception {
		this.mockMvc.perform(get("/greeting")).andExpect(
				MockMvcResultMatchers.status().isOk());

		await().until(() -> {
			then(this.arrayListSpanAccumulator.getSpans().stream().map(Span::getName).collect(
					toList())).containsAll(asList("http/greeting", "message/greetings",
													"message/words", "message/counts"));
		});
	}

	@Configuration
	@SpringBootApplication
	public static class Config {

		@Bean ArrayListSpanAccumulator arrayListSpanAccumulator() {
			return new ArrayListSpanAccumulator();
		}

		@Bean Sampler defaultTraceSampler() {
			return new AlwaysSampler();
		}
	}
}
