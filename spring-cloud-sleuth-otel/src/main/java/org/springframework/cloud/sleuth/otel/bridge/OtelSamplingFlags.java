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

import io.opentelemetry.trace.TraceFlags;

import org.springframework.cloud.sleuth.api.SamplingFlags;

public class OtelSamplingFlags implements SamplingFlags {

	final byte traceFlags;

	public OtelSamplingFlags(byte traceFlags) {
		this.traceFlags = traceFlags;
	}

	@Override
	public Boolean sampled() {
		return TraceFlags.getSampled() == this.traceFlags;
	}

	@Override
	public boolean sampledLocal() {
		// TODO: [OTEL] Not supported
		return false;
	}

	@Override
	public boolean debug() {
		// TODO: [OTEL] Not supported
		return false;
	}

	public static SamplingFlags fromOtel(byte samplingFlags) {
		return new OtelSamplingFlags(samplingFlags);
	}

}
