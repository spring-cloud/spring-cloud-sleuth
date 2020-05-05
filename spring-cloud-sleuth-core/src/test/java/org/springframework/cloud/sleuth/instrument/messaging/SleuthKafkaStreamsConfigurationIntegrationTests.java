package org.springframework.cloud.sleuth.instrument.messaging;

import brave.kafka.streams.KafkaStreamsTracing;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SleuthKafkaStreamsConfigurationIntegrationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
					TraceAutoConfiguration.class,
					SleuthKafkaStreamsConfiguration.class))
			.withUserConfiguration(UserConfig.class);

	@Test
	void should_create_KafkaStreamsTracing() {
		this.contextRunner
				.run(context -> assertThat(context).hasSingleBean(KafkaStreamsTracing.class));
	}

	@Test
	void should_not_create_KafkaStreamsTracing_when_KafkaStreams_not_present() {
		this.contextRunner
				.withClassLoader(new FilteredClassLoader(KafkaStreams.class))
				.run(context -> assertThat(context).doesNotHaveBean(KafkaStreamsTracing.class));
	}

	@Test
	void should_not_create_KafkaStreamsTracing_when_kafkastreams_disabled() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.messaging.kafka.streams.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(KafkaStreamsTracing.class));
	}

	@Test
	void should_not_create_KafkaStreamsTracing_when_messaging_disabled() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.messaging.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(KafkaStreamsTracing.class));
	}

	@Test
	void testKafkaStreamsBuilderFactoryBeanPostProcessor() {
		this.contextRunner
				.run(context -> verify(streamsBuilderFactoryBean).setClientSupplier(any(KafkaClientSupplier.class)));
	}

	private static final StreamsBuilderFactoryBean streamsBuilderFactoryBean = mock(StreamsBuilderFactoryBean.class);

	@Configuration
	static class UserConfig {
		@Bean
		StreamsBuilderFactoryBean streamsBuilderFactoryBean() {
			return streamsBuilderFactoryBean;
		}
	}
}
