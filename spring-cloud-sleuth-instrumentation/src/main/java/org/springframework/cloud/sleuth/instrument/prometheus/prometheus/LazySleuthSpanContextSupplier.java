/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.prometheus.prometheus;

import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.sleuth.Tracer;

/**
 * A {@link SpanContextSupplier} that fetches the {@link Tracer} lazily after singletons
 * are instantiated and uses it onwards.
 *
 * @author Jonatan Ivanov
 */
public class LazySleuthSpanContextSupplier implements SpanContextSupplier, SmartInitializingSingleton {

	private final ObjectProvider<Tracer> tracerProvider;

	private SpanContextSupplier delegate;

	public LazySleuthSpanContextSupplier(ObjectProvider<Tracer> tracerProvider) {
		this.tracerProvider = tracerProvider;
	}

	@Override
	public String getTraceId() {
		return (this.delegate != null) ? this.delegate.getTraceId() : null;
	}

	@Override
	public String getSpanId() {
		return (this.delegate != null) ? this.delegate.getSpanId() : null;
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.delegate = new SleuthSpanContextSupplier(tracerProvider.getIfAvailable());
	}

}
