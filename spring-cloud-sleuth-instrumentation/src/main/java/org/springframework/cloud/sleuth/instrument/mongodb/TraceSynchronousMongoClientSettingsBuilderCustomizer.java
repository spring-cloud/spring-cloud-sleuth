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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.SynchronousContextProvider;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Trace representation of a {@link MongoClientSettingsBuilderCustomizer} that passes
 * through the Reactor context.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceSynchronousMongoClientSettingsBuilderCustomizer implements MongoClientSettingsBuilderCustomizer {

	private final Tracer tracer;

	public TraceSynchronousMongoClientSettingsBuilderCustomizer(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public void customize(MongoClientSettings.Builder clientSettingsBuilder) {
		clientSettingsBuilder.contextProvider(contextProvider(this.tracer));
	}

	static SynchronousContextProvider contextProvider(Tracer tracer) {
		return (SynchronousContextProvider) () -> new SynchronousTraceRequestContext(tracer);
	}

	static class SynchronousTraceRequestContext extends TraceRequestContext {

		SynchronousTraceRequestContext(Tracer tracer) {
			super(context(tracer));
		}

		private static Map<Object, Object> context(Tracer tracer) {
			Map<Object, Object> map = new ConcurrentHashMap<>();
			Span currentSpan = tracer.currentSpan();
			if (currentSpan == null) {
				return map;
			}
			map.put(Span.class, currentSpan);
			map.put(TraceContext.class, currentSpan.context());
			return map;
		}

	}

}
