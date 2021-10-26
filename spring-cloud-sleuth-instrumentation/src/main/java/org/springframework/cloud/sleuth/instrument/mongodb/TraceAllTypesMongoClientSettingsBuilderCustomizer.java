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

package org.springframework.cloud.sleuth.instrument.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.RequestContext;
import com.mongodb.client.SynchronousContextProvider;
import com.mongodb.reactivestreams.client.ReactiveContextProvider;
import org.reactivestreams.Subscriber;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Trace representation of a {@link MongoClientSettingsBuilderCustomizer} that passes both
 * types of context providers.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceAllTypesMongoClientSettingsBuilderCustomizer implements MongoClientSettingsBuilderCustomizer {

	private final Tracer tracer;

	public TraceAllTypesMongoClientSettingsBuilderCustomizer(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public void customize(MongoClientSettings.Builder clientSettingsBuilder) {
		clientSettingsBuilder.contextProvider(new AllTypesContextProvider(this.tracer));
	}

	static class AllTypesContextProvider implements SynchronousContextProvider, ReactiveContextProvider {

		private final SynchronousContextProvider synchronousContextProvider;

		private final ReactiveContextProvider reactiveContextProvider;

		AllTypesContextProvider(Tracer tracer) {
			this.synchronousContextProvider = TraceSynchronousMongoClientSettingsBuilderCustomizer
					.contextProvider(tracer);
			this.reactiveContextProvider = TraceReactiveMongoClientSettingsBuilderCustomizer.contextProvider();
		}

		@Override
		public RequestContext getContext() {
			return this.synchronousContextProvider.getContext();
		}

		@Override
		public RequestContext getContext(Subscriber<?> subscriber) {
			return this.reactiveContextProvider.getContext(subscriber);
		}

	}

}
