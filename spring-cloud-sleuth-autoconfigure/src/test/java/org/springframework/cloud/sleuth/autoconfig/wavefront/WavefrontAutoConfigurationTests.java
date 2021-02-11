/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.autoconfig.wavefront;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import brave.Tracer;
import brave.TracingCustomizer;
import brave.handler.SpanHandler;
import com.wavefront.sdk.appagent.jvm.reporter.WavefrontJvmReporter;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.spring.autoconfigure.ApplicationTagsBuilderCustomizer;
import com.wavefront.spring.autoconfigure.WavefrontAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.wavefront.WavefrontMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.AbstractApplicationContextRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link WavefrontAutoConfiguration}.
 *
 * @author Stephane Nicoll
 * @author Tommy Ludwig
 */
class WavefrontAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(WavefrontAutoConfiguration.class, WavefrontSleuthAutoConfiguration.class));

	@Test
	void applicationTagsIsConfiguredFromPropertiesWhenNoneExists() {
		this.contextRunner
				.withPropertyValues("wavefront.application.name=test-app", "wavefront.application.service=test-service")
				.run((context) -> {
					assertThat(context).hasSingleBean(ApplicationTags.class);
					ApplicationTags tags = context.getBean(ApplicationTags.class);
					assertThat(tags.getApplication()).isEqualTo("test-app");
					assertThat(tags.getService()).isEqualTo("test-service");
					assertThat(tags.getCluster()).isNull();
					assertThat(tags.getShard()).isNull();
					assertThat(tags.getCustomTags()).isEmpty();
				});
	}

	@Test
	void applicationTagsCanBeCustomized() {
		this.contextRunner
				.withPropertyValues("wavefront.application.name=test-app", "wavefront.application.service=test-service")
				.withBean(ApplicationTagsBuilderCustomizer.class,
						() -> (builder) -> builder.cluster("test-cluster").shard("test-shard"))
				.run((context) -> {
					assertThat(context).hasSingleBean(ApplicationTags.class);
					ApplicationTags tags = context.getBean(ApplicationTags.class);
					assertThat(tags.getApplication()).isEqualTo("test-app");
					assertThat(tags.getService()).isEqualTo("test-service");
					assertThat(tags.getCluster()).isEqualTo("test-cluster");
					assertThat(tags.getShard()).isEqualTo("test-shard");
					assertThat(tags.getCustomTags()).isEmpty();
				});
	}

	@Test
	void applicationTagsIsReusedWhenCustomInstanceExists() {
		this.contextRunner
				.withPropertyValues("wavefront.application.name=test-app", "wavefront.application.service=test-service")
				.withBean(ApplicationTags.class,
						() -> new ApplicationTags.Builder("another-app", "another-service").build())
				.run((context) -> {
					assertThat(context).hasSingleBean(ApplicationTags.class);
					ApplicationTags tags = context.getBean(ApplicationTags.class);
					assertThat(tags.getApplication()).isEqualTo("another-app");
					assertThat(tags.getService()).isEqualTo("another-service");
					assertThat(tags.getCluster()).isNull();
					assertThat(tags.getShard()).isNull();
					assertThat(tags.getCustomTags()).isEmpty();
				});
	}

	@Test
	void applicationTagsAreExportedToWavefrontRegistry() {
		this.contextRunner
				.withPropertyValues("wavefront.application.name=test-app", "wavefront.application.service=test-service")
				.with(wavefrontMetrics(() -> mock(WavefrontSender.class))).run((context) -> {
					MeterRegistry registry = context.getBean(MeterRegistry.class);
					registry.counter("my.counter", "env", "qa");
					assertThat(registry.find("my.counter").tags("env", "qa").tags("application", "test-app")
							.tags("service", "test-service").counter()).isNotNull();
				});
	}

	@Test
	void applicationTagsWithFullInformationAreExportedToWavefrontRegistry() {
		this.contextRunner
				.withPropertyValues("wavefront.application.name=test-app", "wavefront.application.service=test-service",
						"wavefront.application.cluster=test-cluster", "wavefront.application.shard=test-shard")
				.with(wavefrontMetrics(() -> mock(WavefrontSender.class))).run((context) -> {
					MeterRegistry registry = context.getBean(MeterRegistry.class);
					registry.counter("my.counter", "env", "qa");
					assertThat(registry.find("my.counter").tags("env", "qa").tags("application", "test-app")
							.tags("service", "test-service").tags("cluster", "test-cluster").tags("shard", "test-shard")
							.counter()).isNotNull();
				});
	}

	@Test
	void applicationTagsAreNotExportedToNonWavefrontRegistry() {
		this.contextRunner
				.withPropertyValues("wavefront.application.name=test-app", "wavefront.application.service=test-service")
				.with(metrics()).withConfiguration(AutoConfigurations.of(SimpleMetricsExportAutoConfiguration.class))
				.run((context) -> {
					MeterRegistry registry = context.getBean(MeterRegistry.class);
					registry.counter("my.counter", "env", "qa");
					assertThat(registry.find("my.counter").tags("env", "qa")).isNotNull();
					assertThat(registry.find("my.counter").tags("env", "qa").tags("application", "test-app")
							.tags("service", "test-service").tags("cluster", "test-cluster").tags("shard", "test-shard")
							.counter()).isNull();
				});
	}

	@Test
	void jvmReporterIsConfiguredWhenNoneExists() {
		this.contextRunner.with(wavefrontMetrics(() -> mock(WavefrontSender.class)))
				.run((context) -> assertThat(context).hasSingleBean(WavefrontJvmReporter.class));
	}

	@Test
	void jvmReporterCanBeDisabled() {
		this.contextRunner.withPropertyValues("wavefront.metrics.extract-jvm-metrics=false")
				.with(wavefrontMetrics(() -> mock(WavefrontSender.class)))
				.run(context -> assertThat(context).doesNotHaveBean(WavefrontJvmReporter.class));
	}

	@Test
	void jvmReporterCanBeCustomized() {
		WavefrontJvmReporter reporter = mock(WavefrontJvmReporter.class);
		this.contextRunner.with(wavefrontMetrics(() -> mock(WavefrontSender.class)))
				.withBean(WavefrontJvmReporter.class, () -> reporter)
				.run((context) -> assertThat(context).getBean(WavefrontJvmReporter.class).isEqualTo(reporter));
	}

	@Test
	void jvmReporterNotConfiguredWithoutWavefrontSender() {
		this.contextRunner.with(metrics())
				.run(context -> assertThat(context).doesNotHaveBean(WavefrontJvmReporter.class));
	}

	@Test
	void tracingWithSleuthIsConfiguredWithWavefrontSender() {
		WavefrontSender sender = mock(WavefrontSender.class);
		this.contextRunner.withPropertyValues().with(wavefrontMetrics(() -> sender)).with(sleuth()).run((context) -> {
			assertThat(context).hasSingleBean(TracingCustomizer.class);
			WavefrontSleuthBraveSpanHandler braveSpanHandler = extractSpanHandler(context.getBean(Tracer.class));
			assertThat(braveSpanHandler.spanHandler).hasFieldOrPropertyWithValue("wavefrontSender", sender);
		});
	}

	@Test
	void tracingWithSleuthWithEmptyEnvironmentUseDefaultTags() {
		this.contextRunner.with(wavefrontMetrics(() -> mock(WavefrontSender.class))).with(sleuth())
				.run(assertSleuthSpanDefaultTags("unnamed_application", "unnamed_service"));
	}

	@Test
	void tracingWithSleuthWithWavefrontTagsAndSpringApplicationNameUseWavefrontTags() {
		this.contextRunner
				.withPropertyValues("wavefront.application.name=wavefront-application",
						"wavefront.application.service=wavefront-service", "spring.application.name=spring-service")
				.with(wavefrontMetrics(() -> mock(WavefrontSender.class))).with(sleuth())
				.run(assertSleuthSpanDefaultTags("wavefront-application", "wavefront-service"));
	}

	@Test
	void tracingWithSleuthWithSpringApplicationNameUseItRatherThanDefault() {
		this.contextRunner.withPropertyValues("spring.application.name=spring-service")
				.with(wavefrontMetrics(() -> mock(WavefrontSender.class))).with(sleuth())
				.run(assertSleuthSpanDefaultTags("unnamed_application", "spring-service"));
	}

	@Test
	void tracingWithSleuthWithCustomApplicationTagsUseThat() {
		this.contextRunner
				.withPropertyValues("wavefront.application.name=wavefront-application",
						"wavefront.application.service=wavefront-service")
				.with(wavefrontMetrics(() -> mock(WavefrontSender.class)))
				.withBean(ApplicationTags.class,
						() -> new ApplicationTags.Builder("custom-application", "custom-service")
								.cluster("custom-cluster").shard("custom-shard").build())
				.with(sleuth()).run(assertSleuthSpanDefaultTags("custom-application", "custom-service",
						"custom-cluster", "custom-shard"));
	}

	@Test
	void tracingWithSleuthWithCustomApplicationTagsAndEmptyValuesFallbackToDefaults() {
		this.contextRunner
				.withPropertyValues("wavefront.application.name=wavefront-application",
						"wavefront.application.service=wavefront-service")
				.with(wavefrontMetrics(() -> mock(WavefrontSender.class)))
				.withBean(ApplicationTags.class,
						() -> new ApplicationTags.Builder("custom-application", "custom-service").build())
				.with(sleuth())
				.run(assertSleuthSpanDefaultTags("custom-application", "custom-service", "none", "none"));
	}

	private ContextConsumer<AssertableApplicationContext> assertSleuthSpanDefaultTags(String applicationName,
			String serviceName) {
		return assertSleuthSpanDefaultTags(applicationName, serviceName, "none", "none");
	}

	private ContextConsumer<AssertableApplicationContext> assertSleuthSpanDefaultTags(String applicationName,
			String serviceName, String cluster, String shard) {
		return (context) -> {
			assertThat(context).hasSingleBean(TracingCustomizer.class);
			WavefrontSleuthBraveSpanHandler braveSpanHandler = extractSpanHandler(context.getBean(Tracer.class));
			assertThat(braveSpanHandler.spanHandler.getDefaultTags()).contains(
					new Pair<>("application", applicationName), new Pair<>("service", serviceName),
					new Pair<>("cluster", cluster), new Pair<>("shard", shard));
		};
	}

	@SuppressWarnings("unchecked")
	@Test
	void tracingWithSleuthCanBeConfigured() {
		WavefrontSender sender = mock(WavefrontSender.class);
		this.contextRunner.withPropertyValues()
				.withPropertyValues("wavefront.tracing.red-metrics-custom-tag-keys=region,test")
				.with(wavefrontMetrics(() -> sender)).with(sleuth()).run((context) -> {
					assertThat(context).hasSingleBean(TracingCustomizer.class);
					WavefrontSleuthBraveSpanHandler braveSpanHandler = extractSpanHandler(
							context.getBean(Tracer.class));
					WavefrontSleuthSpanHandler spanHandler = braveSpanHandler.spanHandler;
					Set<String> traceDerivedCustomTagKeys = (Set<String>) ReflectionTestUtils.getField(spanHandler,
							"traceDerivedCustomTagKeys");
					assertThat(traceDerivedCustomTagKeys).containsExactlyInAnyOrder("region", "test");
				});
	}

	@Test
	void tracingWithOpenTracingBacksOffWhenSpringCloudSleuthIsAvailable() {
		this.contextRunner.with(wavefrontMetrics(() -> mock(WavefrontSender.class)))
				.run((context) -> assertThat(context).hasSingleBean(TracingCustomizer.class)
						.doesNotHaveBean(io.opentracing.Tracer.class));
	}

	@Test
	void tracingIsDisabledWhenOpenTracingAndSleuthAreNotAvailable() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(WavefrontAutoConfiguration.class))
				.withClassLoader(new FilteredClassLoader("org.springframework.cloud.sleuth", "io.opentracing"))
				.with(wavefrontMetrics(() -> mock(WavefrontSender.class))).run((context) -> assertThat(context)
						.doesNotHaveBean(TracingCustomizer.class).doesNotHaveBean(io.opentracing.Tracer.class));
	}

	@Test
	void tracingCanBeDisabled() {
		this.contextRunner.withPropertyValues("wavefront.tracing.enabled=false")
				.with(wavefrontMetrics(() -> mock(WavefrontSender.class))).run((context) -> assertThat(context)
						.doesNotHaveBean(TracingCustomizer.class).doesNotHaveBean(io.opentracing.Tracer.class));
	}

	@Test
	void tracingIsNotConfiguredWithNonWavefrontRegistry() {
		this.contextRunner.with(metrics()).run((context) -> assertThat(context).doesNotHaveBean(Tracer.class));
	}

	@SuppressWarnings("ConstantConditions")
	private WavefrontSleuthBraveSpanHandler extractSpanHandler(Tracer tracer) {
		SpanHandler[] handlers = (SpanHandler[]) ReflectionTestUtils.getField(
				ReflectionTestUtils.getField(ReflectionTestUtils.getField(tracer, "spanHandler"), "delegate"),
				"handlers");
		return (WavefrontSleuthBraveSpanHandler) handlers[1];
	}

	@SuppressWarnings("unchecked")
	private static <T extends AbstractApplicationContextRunner<?, ?, ?>> Function<T, T> wavefrontMetrics(
			Supplier<WavefrontSender> wavefrontSender) {
		return (runner) -> (T) runner.withBean(WavefrontSender.class, wavefrontSender)
				.withConfiguration(AutoConfigurations.of(WavefrontMetricsExportAutoConfiguration.class))
				.with(metrics());
	}

	@SuppressWarnings("unchecked")
	private static <T extends AbstractApplicationContextRunner<?, ?, ?>> Function<T, T> metrics() {
		return (runner) -> (T) runner.withPropertyValues("management.metrics.use-global-registry=false")
				.withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class,
						CompositeMeterRegistryAutoConfiguration.class));
	}

	@SuppressWarnings("unchecked")
	private static <T extends AbstractApplicationContextRunner<?, ?, ?>> Function<T, T> sleuth() {
		return (runner) -> (T) runner.withConfiguration(AutoConfigurations.of(BraveAutoConfiguration.class));
	}

}
