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
import brave.handler.MutableSpan;
import brave.test.TestSpanHandler;
import com.mongodb.MongoClientSettings;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import org.assertj.core.api.BDDAssertions;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.DisableSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(properties = { "test.mongo.mock.enabled=false", "spring.sleuth.tracer.mode=BRAVE" })
class BraveMongoDbAutoConfigurationTests {

	@Test
	void should_record_a_span_when_working_with_mongodb_commands(@Autowired TestSpanHandler handler) {
		BDDAssertions.then(handler.spans()).isNotEmpty();
		MutableSpan span = handler.get(0);
		then(span.traceId()).isNotEmpty();
		then(span.tags()).containsKey("mongodb.command");
		then(span.remoteServiceName()).contains("mongodb");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@DisableSecurity
	static class TestTraceMongoDbAutoConfiguration {

		@Bean
		TestSpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		@Bean
		TraceMongoClientSettingsBuilderCustomizer testMongoClientSettingsBuilderCustomizer(Tracing tracing) {
			return new TestMongoClientSettingsBuilderCustomizer(tracing);
		}

	}

}

class TestMongoClientSettingsBuilderCustomizer extends TraceMongoClientSettingsBuilderCustomizer {

	TestMongoClientSettingsBuilderCustomizer(Tracing tracing) {
		super(tracing);
	}

	@Override
	public void customize(MongoClientSettings.Builder clientSettingsBuilder) {
		super.customize(clientSettingsBuilder);
		CommandListener listener = clientSettingsBuilder.build().getCommandListeners().get(0);
		listener.commandStarted(new CommandStartedEvent(0, null, "", "", BDDMockito.mock(BsonDocument.class)));
		listener.commandSucceeded(new CommandSucceededEvent(1, null, "", BDDMockito.mock(BsonDocument.class), 100));
	}

}
