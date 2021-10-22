/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.instrument.mongodb;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.mongodb.TraceMongoClientSettingsBuilderCustomizer;
import org.springframework.cloud.sleuth.instrument.mongodb.TraceReactiveMongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TraceMongodbAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true").withConfiguration(
					AutoConfigurations.of(TraceNoOpAutoConfiguration.class, TraceMongoDbAutoConfiguration.class));

	@Test
	void should_not_create_reactive_customizer_when_reactive_context_missing() {
		this.contextRunner
				.withClassLoader(new FilteredClassLoader(brave.mongodb.MongoDBTracing.class,
						com.mongodb.reactivestreams.client.ReactiveContextProvider.class))
				.run((context) -> assertThat(context).hasSingleBean(TraceMongoClientSettingsBuilderCustomizer.class)
						.doesNotHaveBean(TraceReactiveMongoClientSettingsBuilderCustomizer.class));
	}

	@Test
	void should_create_both_customizers_when_reactive_context_present() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(brave.mongodb.MongoDBTracing.class))
				.run((context) -> assertThat(context).hasSingleBean(TraceMongoClientSettingsBuilderCustomizer.class)
						.hasSingleBean(TraceReactiveMongoClientSettingsBuilderCustomizer.class));
	}

}
