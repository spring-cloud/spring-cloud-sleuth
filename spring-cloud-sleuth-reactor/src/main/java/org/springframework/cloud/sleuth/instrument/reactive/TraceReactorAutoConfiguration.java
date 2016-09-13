/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.reactive;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ThreadFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * that wraps project Reactor's components in their trace representations.
 *
 * @author Marcin Grzejszczak
 * @author Stephane Maldini
 * @since 1.0.9
 */
@Configuration
@ConditionalOnClass(Scheduler.class)
@ConditionalOnBean({Tracer.class, TraceKeys.class})
@ConditionalOnProperty(value = "spring.sleuth.reactor.enabled", matchIfMissing = true)
public class TraceReactorAutoConfiguration {

	@Bean
	ReactorHookRegistrar reactorHookRegistrar(Tracer tracer, TraceKeys traceKeys) {
		return new ReactorHookRegistrar(tracer, traceKeys);
	}

	static class ReactorHookRegistrar {

		private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

		ReactorHookRegistrar(final Tracer tracer, final TraceKeys traceKeys) {
			if (log.isDebugEnabled()) {
				log.debug("Registering the Trace Scheduler Single Factory");
			}
			Schedulers.setFactory(new Schedulers.Factory() {
				@Override public Scheduler newSingle(ThreadFactory threadFactory) {
					return new TraceScheduler(Schedulers.Factory.super.newSingle(threadFactory),
							tracer, traceKeys);
				}

			});
		}
	}
}
