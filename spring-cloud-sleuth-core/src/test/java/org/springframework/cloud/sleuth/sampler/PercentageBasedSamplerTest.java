package org.springframework.cloud.sleuth.sampler;

import org.junit.Test;
import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceAccessor;
import org.springframework.util.JdkIdGenerator;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.data.Percentage.withPercentage;

public class PercentageBasedSamplerTest {

	SamplerConfiguration samplerConfiguration = new SamplerConfiguration();
	TraceAccessor traceAccessor = traceReturningSpanWithUuid();
	StringToUuidConverter stringToUuidConverter = new DefaultStringToUuidConverter();

	@Test
	public void should_pass_all_samples_when_config_has_1_percentage() throws Exception {
		samplerConfiguration.setPercentage(1f);

		for (int i = 0; i < 10; i++) {
			then(new PercentageBasedSampler(samplerConfiguration, traceAccessor, stringToUuidConverter).next(null)).isTrue();
		}

	}

	@Test
	public void should_reject_all_samples_when_config_has_0_percentage() throws Exception {
		samplerConfiguration.setPercentage(0f);

		for (int i = 0; i < 10; i++) {
			then(new PercentageBasedSampler(samplerConfiguration, traceAccessor, stringToUuidConverter).next(null)).isFalse();
		}
	}

	@Test
	public void should_reject_sample_when_trace_id_is_invalid() throws Exception {
		samplerConfiguration.setPercentage(1f);

		boolean passed = new PercentageBasedSampler(samplerConfiguration, traceReturningSpanWithInvalidUuid(), stringToUuidConverter).next(null);

		then(passed).isFalse();
	}

	@Test
	public void should_pass_given_percent_of_samples() throws Exception {
		samplerConfiguration.setPercentage(0.3f);

		int numberOfSampledElements = countNumberOfSampledElements();

		//TODO: We have random data thus tests can fail due to this percentage. If that's the case we should change it to .isPositive()
		then(numberOfSampledElements).isCloseTo(30, withPercentage(30));
	}

	private int countNumberOfSampledElements() {
		int passedCounter = 0;
		for (int i = 0; i < 100; i++) {
			boolean passed = new PercentageBasedSampler(samplerConfiguration, traceReturningSpanWithUuid(), stringToUuidConverter).next(null);
			passedCounter = passedCounter + (passed ? 1 : 0);
		}
		return passedCounter;
	}

	private TraceAccessor traceReturningSpanWithUuid() {
		return new TraceAccessor() {
			@Override
			public Span getCurrentSpan() {
				return MilliSpan.builder().traceId(new JdkIdGenerator().generateId().toString()).build();
			}

			@Override
			public boolean isTracing() {
				return true;
			}
		};
	}

	private TraceAccessor traceReturningSpanWithInvalidUuid() {
		return new TraceAccessor() {
			@Override
			public Span getCurrentSpan() {
				return MilliSpan.builder().traceId("invalid uuid").build();
			}

			@Override
			public boolean isTracing() {
				return true;
			}
		};
	}

}