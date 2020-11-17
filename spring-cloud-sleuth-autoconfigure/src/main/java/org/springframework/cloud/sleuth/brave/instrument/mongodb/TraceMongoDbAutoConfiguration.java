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

package org.springframework.cloud.sleuth.brave.instrument.mongodb;

import brave.Tracing;
import brave.mongodb.MongoDBTracing;
import com.mongodb.MongoClientSettings;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.cloud.sleuth.brave.autoconfig.BraveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables MongoDb span information propagation.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(Tracing.class)
@AutoConfigureAfter(BraveAutoConfiguration.class)
@AutoConfigureBefore(MongoAutoConfiguration.class)
@ConditionalOnProperty(value = "spring.sleuth.mongodb.enabled", matchIfMissing = true)
@ConditionalOnClass({ MongoClientSettings.Builder.class, MongoDBTracing.class })
class TraceMongoDbAutoConfiguration {

	@Bean
	// for tests
	@ConditionalOnMissingBean(TraceMongoClientSettingsBuilderCustomizer.class)
	MongoClientSettingsBuilderCustomizer traceMongoClientSettingsBuilderCustomizer(Tracing tracing) {
		return new TraceMongoClientSettingsBuilderCustomizer(tracing);
	}

}

class TraceMongoClientSettingsBuilderCustomizer implements MongoClientSettingsBuilderCustomizer {

	private final Tracing tracing;

	TraceMongoClientSettingsBuilderCustomizer(Tracing tracing) {
		this.tracing = tracing;
	}

	@Override
	public void customize(MongoClientSettings.Builder clientSettingsBuilder) {
		clientSettingsBuilder.addCommandListener(MongoDBTracing.create(this.tracing).commandListener());
	}

}
