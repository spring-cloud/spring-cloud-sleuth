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

package org.springframework.cloud.sleuth.api;

import java.util.List;

import org.springframework.lang.Nullable;

public interface TraceContext extends SamplingFlags {

	long traceIdHigh();

	long traceId();

	// This is the first span ID that became a Span or ScopedSpan
	long localRootId();

	boolean isLocalRoot();

	@Nullable
	Long parentId();

	long parentIdAsLong();

	long spanId();

	boolean shared();

	List<Object> extra();

	@Nullable
	<T> T findExtra(Class<T> type);

	TraceContext.Builder toBuilder();

	String traceIdString();

	@Nullable
	String parentIdString();

	@Nullable
	String localRootIdString();

	String spanIdString();

	interface Builder {

		Builder traceIdHigh(long traceIdHigh);

		Builder traceId(long traceId);

		Builder parentId(long parentId);

		Builder parentId(@Nullable Long parentId);

		Builder spanId(long spanId);

		Builder sampledLocal(boolean sampledLocal);

		Builder sampled(boolean sampled);

		Builder sampled(@Nullable Boolean sampled);

		Builder debug(boolean debug);

		Builder shared(boolean shared);

		Builder clearExtra();

		Builder addExtra(Object extra);

		TraceContext build();

	}

}
