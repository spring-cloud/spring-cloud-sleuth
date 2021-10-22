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

package org.springframework.cloud.sleuth.brave.instrument.mongodb;

import brave.Tracing;
import brave.mongodb.MongoDBTracing;
import com.mongodb.MongoClientSettings;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;

/**
 * Trace representation of a {@link MongoClientSettingsBuilderCustomizer}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class TraceMongoClientSettingsBuilderCustomizer implements MongoClientSettingsBuilderCustomizer {

	private final Tracing tracing;

	public TraceMongoClientSettingsBuilderCustomizer(Tracing tracing) {
		this.tracing = tracing;
	}

	@Override
	public void customize(MongoClientSettings.Builder clientSettingsBuilder) {
		clientSettingsBuilder.addCommandListener(MongoDBTracing.create(this.tracing).commandListener());
	}

}
