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
import brave.handler.SpanHandler;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.wavefront.WavefrontMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.AbstractApplicationContextRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link WavefrontSleuthAutoConfiguration}.
 *
 * @author Stephane Nicoll
 * @author Tommy Ludwig
 */
class WavefrontAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(WavefrontSleuthAutoConfiguration.class))
			.withUserConfiguration(Config.class);

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
	void tracingWithSleuthWithWavefrontTagsAndSpringApplicationNameUseWavefrontTags() {
		this.contextRunner
				.withPropertyValues("wavefront.application.name=wavefront-application",
						"wavefront.application.service=wavefront-service", "spring.application.name=spring-service")
				.with(wavefrontMetrics(() -> mock(WavefrontSender.class))).with(sleuth())
				.run(assertSleuthSpanDefaultTags("wavefront-application", "wavefront-service"));
	}

	private ContextConsumer<AssertableApplicationContext> assertSleuthSpanDefaultTags(String applicationName,
			String serviceName) {
		return assertSleuthSpanDefaultTags(applicationName, serviceName, "none", "none");
	}

	private ContextConsumer<AssertableApplicationContext> assertSleuthSpanDefaultTags(String applicationName,
			String serviceName, String cluster, String shard) {
		return (context) -> {
			assertThat(context).hasSingleBean(WavefrontTracingCustomizer.class);
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
				.withPropertyValues("spring.sleuth.wavefront.red-metrics-custom-tag-keys=region,test")
				.with(wavefrontMetrics(() -> sender)).with(sleuth()).run((context) -> {
					assertThat(context).hasSingleBean(WavefrontTracingCustomizer.class);
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
				.run((context) -> assertThat(context).hasSingleBean(WavefrontTracingCustomizer.class)
						.doesNotHaveBean(io.opentracing.Tracer.class));
	}

	@Test
	void tracingCanBeDisabled() {
		this.contextRunner.withPropertyValues("spring.sleuth.wavefront.enabled=false")
				.with(wavefrontMetrics(() -> mock(WavefrontSender.class)))
				.run((context) -> assertThat(context).doesNotHaveBean(WavefrontTracingCustomizer.class)
						.doesNotHaveBean(io.opentracing.Tracer.class));
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

	@Configuration
	static class Config {

		@Bean
		ApplicationTags applicationTags(Environment environment) {
			return createFromProperties(environment);
		}

		public ApplicationTags createFromProperties(Environment environment) {
			String application = environment.getProperty("wavefront.application.name");
			String service = environment.getProperty("wavefront.application.service");
			service = (StringUtils.hasText(application)) ? service : defaultServiceName(environment);
			ApplicationTags.Builder builder = new ApplicationTags.Builder(application, service);
			builder.cluster(environment.getProperty("wavefront.application.cluster"));
			builder.shard(environment.getProperty("wavefront.application.shard"));
			return builder.build();
		}

		private String defaultServiceName(Environment environment) {
			return environment.getProperty("spring.application.name", "unnamed_service");
		}

	}

}
