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

package org.springframework.cloud.sleuth.brave.bridge;

import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.internal.EncodingUtils;

/**
 * Brave implementation of a {@link TraceContext.Builder}.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
class BraveTraceContextBuilder implements TraceContext.Builder {

	brave.propagation.TraceContext.Builder delegate = brave.propagation.TraceContext.newBuilder();

	@Override
	public TraceContext.Builder traceId(String traceId) {
		long[] fromString = EncodingUtils.fromString(traceId);
		if (fromString.length == 2) {
			this.delegate.traceIdHigh(fromString[0]);
			this.delegate.traceId(fromString[1]);
		}
		else {
			this.delegate.traceId(fromString[0]);
		}
		return this;
	}

	@Override
	public TraceContext.Builder parentId(String traceId) {
		long[] fromString = EncodingUtils.fromString(traceId);
		this.delegate.parentId(fromString[fromString.length == 2 ? 1 : 0]);
		return this;
	}

	@Override
	public TraceContext.Builder spanId(String spanId) {
		long[] fromString = EncodingUtils.fromString(spanId);
		this.delegate.spanId(fromString[fromString.length == 2 ? 1 : 0]);
		return this;
	}

	@Override
	public TraceContext.Builder sampled(Boolean sampled) {
		this.delegate.sampled(sampled);
		return this;
	}

	@Override
	public TraceContext build() {
		brave.propagation.TraceContext context = this.delegate.build();
		return BraveTraceContext.fromBrave(context);
	}

}
