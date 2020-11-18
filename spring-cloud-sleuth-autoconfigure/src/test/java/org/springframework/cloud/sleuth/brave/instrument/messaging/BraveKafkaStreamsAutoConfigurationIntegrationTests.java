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

package org.springframework.cloud.sleuth.brave.instrument.messaging;

import javax.annotation.PostConstruct;

import brave.Tracing;
import brave.kafka.streams.KafkaStreamsTracing;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.sleuth.brave.autoconfig.BraveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class BraveKafkaStreamsAutoConfigurationIntegrationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(BraveAutoConfiguration.class, BraveKafkaStreamsAutoConfiguration.class))
			.withUserConfiguration(UserConfig.class);

	@Test
	void should_create_KafkaStreamsTracing() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(KafkaStreamsTracing.class));
	}

	@Test
	void should_not_create_KafkaStreamsTracing_when_KafkaStreams_not_present() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(KafkaStreams.class))
				.run(context -> assertThat(context).doesNotHaveBean(KafkaStreamsTracing.class));
	}

	@Test
	void should_not_create_KafkaStreamsTracing_when_kafkastreams_disabled() {
		this.contextRunner.withPropertyValues("spring.sleuth.messaging.kafka.streams.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(KafkaStreamsTracing.class));
	}

	@Test
	void should_not_create_KafkaStreamsTracing_when_messaging_disabled() {
		this.contextRunner.withPropertyValues("spring.sleuth.messaging.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(KafkaStreamsTracing.class));
	}

	@Test
	void should_set_KafkaClientSupplier_on_StreamsBuilderFactoryBean() {
		this.contextRunner.run(context -> verify(UserConfig.streamsBuilderFactoryBean)
				.setClientSupplier(any(KafkaClientSupplier.class)));
	}

	@Test
	void should_not_complain_about_eager_initialization() {
		this.contextRunner.withUserConfiguration(EagerInitializationConfig.class)
				.run(context -> verify(UserConfig.streamsBuilderFactoryBean)
						.setClientSupplier(any(KafkaClientSupplier.class)));
	}

	@AfterEach
	void afterEach(CapturedOutput output) {
		assertThat(output).doesNotContain("is not eligible for getting processed by all BeanPostProcessors");
	}

	@Configuration(proxyBeanMethods = false)
	static class UserConfig {

		static StreamsBuilderFactoryBean streamsBuilderFactoryBean;

		@Bean
		StreamsBuilderFactoryBean streamsBuilderFactoryBean() {
			streamsBuilderFactoryBean = mock(StreamsBuilderFactoryBean.class);
			return UserConfig.streamsBuilderFactoryBean;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class EagerInitializationConfig {

		@Bean
		EagerInitializationComponent eagerInitializationComponent() {
			return new EagerInitializationComponent();
		}

	}

	static class EagerInitializationComponent {

		@Autowired
		private Tracing tracing;

		private KafkaStreamsTracing kafkaStreamsTracing;

		@PostConstruct
		void init() {
			kafkaStreamsTracing = KafkaStreamsTracing.create(tracing);
		}

	}

}
