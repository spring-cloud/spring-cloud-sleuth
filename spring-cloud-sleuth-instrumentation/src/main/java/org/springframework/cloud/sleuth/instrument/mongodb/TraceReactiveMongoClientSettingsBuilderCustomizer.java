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

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.ReactiveContextProvider;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;

/**
 * Trace representation of a {@link MongoClientSettingsBuilderCustomizer} that passes
 * through the Reactor context.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceReactiveMongoClientSettingsBuilderCustomizer implements MongoClientSettingsBuilderCustomizer {

	@Override
	public void customize(MongoClientSettings.Builder clientSettingsBuilder) {
		clientSettingsBuilder.contextProvider(contextProvider());
	}

	static ReactiveContextProvider contextProvider() {
		return (ReactiveContextProvider) subscriber -> {
			if (subscriber instanceof CoreSubscriber) {
				return new ReactiveTraceRequestContext(((CoreSubscriber<?>) subscriber).currentContext());
			}
			return new ReactiveTraceRequestContext(Context.empty());
		};
	}

	static class ReactiveTraceRequestContext extends TraceRequestContext {

		ReactiveTraceRequestContext(ContextView context) {
			super(new ConcurrentHashMap<>(context.stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue))));
		}

	}

}
