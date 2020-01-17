/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.function.Supplier;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import reactor.util.context.Context;

import org.springframework.beans.factory.BeanFactory;

/**
 * Supplier to lazily start a {@link SpanSubscription}.
 *
 * @param <T> type of returned subscription
 * @author Marcin Grzejszczak
 */
final class SpanSubscriptionProvider<T> implements Supplier<SpanSubscription<T>> {

	private static final Log log = LogFactory.getLog(SpanSubscriptionProvider.class);

	final BeanFactory beanFactory;

	final Subscriber<? super T> subscriber;

	final Context context;

	final String name;

	private volatile CurrentTraceContext currentTraceContext;

	SpanSubscriptionProvider(BeanFactory beanFactory, Subscriber<? super T> subscriber,
			Context context, String name) {
		this.beanFactory = beanFactory;
		this.subscriber = subscriber;
		this.context = context;
		this.name = name;
		if (log.isTraceEnabled()) {
			log.trace("Spring context [" + beanFactory + "], Reactor context [" + context
					+ "], name [" + name + "]");
		}
	}

	@Override
	public SpanSubscription<T> get() {
		return newCoreSubscriber(currentTraceContext());
	}

	SpanSubscription<T> newCoreSubscriber(CurrentTraceContext currentTraceContext) {
		TraceContext root = this.context.hasKey(TraceContext.class)
				? this.context.get(TraceContext.class) : currentTraceContext.get();
		return new ScopePassingSpanSubscriber<>(this.subscriber, this.context,
				currentTraceContext, root);
	}

	private CurrentTraceContext currentTraceContext() {
		if (this.currentTraceContext == null) {
			try {
				this.currentTraceContext = this.beanFactory
						.getBean(CurrentTraceContext.class);
			}
			catch (Exception ex) {
				if (log.isDebugEnabled()) {
					log.debug(
							"Exception occurred while trying to get the currentTraceContext bean. Will return a default instance",
							ex);
				}
				return CurrentTraceContext.Default.create();
			}
		}
		return this.currentTraceContext;
	}

}
