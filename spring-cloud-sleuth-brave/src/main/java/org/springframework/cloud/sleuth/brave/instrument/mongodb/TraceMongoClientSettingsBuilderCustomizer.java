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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.mongodb.MongoClientSettings;
import com.mongodb.RequestContext;
import com.mongodb.reactivestreams.client.ReactiveContextProvider;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Trace representation of a {@link MongoClientSettingsBuilderCustomizer}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class TraceMongoClientSettingsBuilderCustomizer implements MongoClientSettingsBuilderCustomizer {

	private final Tracer tracer;

	private final CurrentTraceContext currentTraceContext;

	public TraceMongoClientSettingsBuilderCustomizer(Tracer tracer, CurrentTraceContext currentTraceContext) {
		this.tracer = tracer;
		this.currentTraceContext = currentTraceContext;
	}

	@Override
	public void customize(MongoClientSettings.Builder clientSettingsBuilder) {
		// TODO: This only when reactor is present
		clientSettingsBuilder.contextProvider((ReactiveContextProvider) subscriber -> {
			if (subscriber instanceof CoreSubscriber) {
				return new TraceRequestContext(((CoreSubscriber<?>) subscriber).currentContext());
			}
			return new TraceRequestContext(Context.empty());
		});

		// TODO: This only when reactor is missing
		clientSettingsBuilder.addCommandListener(new TraceMongoCommandListener(this.tracer, this.currentTraceContext));

	}

	static class TraceRequestContext implements RequestContext {

		private final Map<Object, Object> map = new ConcurrentHashMap<>();

		TraceRequestContext(ContextView context) {
			context.stream().forEach(entry -> map.put(entry.getKey(), entry.getValue()));
		}

		@Override
		public <T> T get(Object key) {
			return (T) map.get(key);
		}

		@Override
		public boolean hasKey(Object key) {
			return map.containsKey(key);
		}

		@Override
		public boolean isEmpty() {
			return map.isEmpty();
		}

		@Override
		public void put(Object key, Object value) {
			map.put(key, value);
		}

		@Override
		public void delete(Object key) {
			map.remove(key);
		}

		@Override
		public int size() {
			return map.size();
		}

		@Override
		public Stream<Map.Entry<Object, Object>> stream() {
			return map.entrySet().stream();
		}

	}

}
