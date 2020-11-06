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

package org.springframework.cloud.sleuth.otel.bridge;

import java.util.Objects;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.spi.MeterProviderFactory;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.spi.TracerProviderFactory;
import io.opentelemetry.context.propagation.ContextPropagators;

/**
 * Sleuth implementation of a {@link OpenTelemetry}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class OtelOpenTelemetry implements OpenTelemetry {

	private final TracerProviderFactory tracerProviderFactory;

	private final MeterProviderFactory meterProviderFactory;

	private final TracerProvider tracerProvider;

	private final MeterProvider meterProvider;

	private final ContextPropagators contextPropagators;

	public OtelOpenTelemetry(TracerProviderFactory tracerProviderFactory, MeterProviderFactory meterProviderFactory,
			TracerProvider tracerProvider, MeterProvider meterProvider, ContextPropagators contextPropagators) {
		this.tracerProviderFactory = tracerProviderFactory;
		this.meterProviderFactory = meterProviderFactory;
		this.tracerProvider = tracerProvider;
		this.meterProvider = meterProvider;
		this.contextPropagators = contextPropagators;
	}

	@Override
	public TracerProvider getTracerProvider() {
		return this.tracerProvider;
	}

	@Override
	public MeterProvider getMeterProvider() {
		return this.meterProvider;
	}

	@Override
	public ContextPropagators getPropagators() {
		return this.contextPropagators;
	}

	@Override
	public io.opentelemetry.api.OpenTelemetry.Builder<OtelOpenTelemetry.Builder> toBuilder() {
		return new Builder(this.meterProviderFactory, this.tracerProviderFactory);
	}

	static class Builder implements io.opentelemetry.api.OpenTelemetry.Builder<OtelOpenTelemetry.Builder> {

		private ContextPropagators propagators;

		private TracerProvider tracerProvider;

		private MeterProvider meterProvider;

		private final MeterProviderFactory meterProviderFactory;

		private final TracerProviderFactory tracerProviderFactory;

		Builder(MeterProviderFactory meterProviderFactory, TracerProviderFactory tracerProviderFactory) {
			this.meterProviderFactory = meterProviderFactory;
			this.tracerProviderFactory = tracerProviderFactory;
		}

		public OtelOpenTelemetry.Builder setTracerProvider(TracerProvider tracerProvider) {
			Objects.requireNonNull(tracerProvider, "tracerProvider");
			this.tracerProvider = tracerProvider;
			return this;
		}

		public OtelOpenTelemetry.Builder setMeterProvider(MeterProvider meterProvider) {
			Objects.requireNonNull(meterProvider, "meterProvider");
			this.meterProvider = meterProvider;
			return this;
		}

		public OtelOpenTelemetry.Builder setPropagators(ContextPropagators propagators) {
			Objects.requireNonNull(propagators, "propagators");
			this.propagators = propagators;
			return this;
		}

		public OpenTelemetry build() {
			MeterProvider meterProvider = this.meterProvider;
			if (meterProvider == null) {
				meterProvider = this.meterProviderFactory.create();
			}

			TracerProvider tracerProvider = this.tracerProvider;
			if (tracerProvider == null) {
				tracerProvider = this.tracerProviderFactory.create();
			}

			return new OtelOpenTelemetry(tracerProviderFactory, meterProviderFactory, tracerProvider, meterProvider,
					this.propagators);
		}

	}

}
