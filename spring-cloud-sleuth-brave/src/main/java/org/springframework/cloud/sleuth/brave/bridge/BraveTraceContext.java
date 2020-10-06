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

package org.springframework.cloud.sleuth.brave.bridge;

import java.util.List;
import java.util.Objects;

import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.lang.Nullable;

public class BraveTraceContext implements TraceContext {

	final brave.propagation.TraceContext traceContext;

	public BraveTraceContext(brave.propagation.TraceContext traceContext) {
		this.traceContext = traceContext;
	}

	@Override
	public long traceIdHigh() {
		return this.traceContext.traceIdHigh();
	}

	@Override
	public long traceId() {
		return this.traceContext.traceId();
	}

	@Override
	public long localRootId() {
		return this.traceContext.localRootId();
	}

	@Override
	public boolean isLocalRoot() {
		return this.traceContext.isLocalRoot();
	}

	@Override
	public Long parentId() {
		return this.traceContext.parentId();
	}

	@Override
	public long parentIdAsLong() {
		return this.traceContext.parentIdAsLong();
	}

	@Override
	public long spanId() {
		return this.traceContext.spanId();
	}

	@Override
	public boolean shared() {
		return this.traceContext.shared();
	}

	@Override
	public List<Object> extra() {
		return this.traceContext.extra();
	}

	@Override
	@Nullable
	public <T> T findExtra(Class<T> type) {
		return this.traceContext.findExtra(type);
	}

	@Override
	public TraceContext.Builder toBuilder() {
		return new BraveTraceContextBuilder(this.traceContext.toBuilder());
	}

	@Override
	public String traceIdString() {
		return this.traceContext.traceIdString();
	}

	@Override
	@Nullable
	public String parentIdString() {
		return this.traceContext.parentIdString();
	}

	@Override
	@Nullable
	public String localRootIdString() {
		return this.traceContext.localRootIdString();
	}

	@Override
	public String spanIdString() {
		return this.traceContext.spanIdString();
	}

	@Override
	public String toString() {
		return this.traceContext != null ? this.traceContext.toString() : "null";
	}

	@Override
	public boolean equals(Object o) {
		return Objects.equals(this.traceContext, o);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.traceContext);
	}

	@Nullable
	public Boolean sampled() {
		return this.traceContext.sampled();
	}

	@Override
	public boolean sampledLocal() {
		return this.traceContext.sampledLocal();
	}

	@Override
	public boolean debug() {
		return this.traceContext.debug();
	}

	public static brave.propagation.TraceContext toBrave(TraceContext traceContext) {
		if (traceContext == null) {
			return null;
		}
		return ((BraveTraceContext) traceContext).traceContext;
	}

	public static TraceContext fromBrave(brave.propagation.TraceContext traceContext) {
		return new BraveTraceContext(traceContext);
	}

}

class BraveTraceContextBuilder implements TraceContext.Builder {

	private final brave.propagation.TraceContext.Builder delegate;

	BraveTraceContextBuilder(brave.propagation.TraceContext.Builder delegate) {
		this.delegate = delegate;
	}

	@Override
	public TraceContext.Builder traceIdHigh(long traceIdHigh) {
		return new BraveTraceContextBuilder(this.delegate.traceIdHigh(traceIdHigh));
	}

	@Override
	public TraceContext.Builder traceId(long traceId) {
		return new BraveTraceContextBuilder(this.delegate.traceId(traceId));
	}

	@Override
	public TraceContext.Builder parentId(long parentId) {
		return new BraveTraceContextBuilder(this.delegate.parentId(parentId));
	}

	@Override
	public TraceContext.Builder parentId(Long parentId) {
		return new BraveTraceContextBuilder(this.delegate.parentId(parentId));
	}

	@Override
	public TraceContext.Builder spanId(long spanId) {
		return new BraveTraceContextBuilder(this.delegate.spanId(spanId));
	}

	@Override
	public TraceContext.Builder sampledLocal(boolean sampledLocal) {
		return new BraveTraceContextBuilder(this.delegate.sampledLocal(sampledLocal));
	}

	@Override
	public TraceContext.Builder sampled(boolean sampled) {
		return new BraveTraceContextBuilder(this.delegate.sampled(sampled));
	}

	@Override
	public TraceContext.Builder sampled(Boolean sampled) {
		return new BraveTraceContextBuilder(this.delegate.sampled(sampled));
	}

	@Override
	public TraceContext.Builder debug(boolean debug) {
		return new BraveTraceContextBuilder(this.delegate.debug(debug));
	}

	@Override
	public TraceContext.Builder shared(boolean shared) {
		return new BraveTraceContextBuilder(this.delegate.shared(shared));
	}

	@Override
	public TraceContext.Builder clearExtra() {
		return new BraveTraceContextBuilder(this.delegate.clearExtra());
	}

	@Override
	public TraceContext.Builder addExtra(Object extra) {
		return new BraveTraceContextBuilder(this.delegate.addExtra(extra));
	}

	@Override
	public TraceContext build() {
		return new BraveTraceContext(this.delegate.build());
	}

}