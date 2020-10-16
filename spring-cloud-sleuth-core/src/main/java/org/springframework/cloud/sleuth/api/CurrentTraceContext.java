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

import java.io.Closeable;

import org.springframework.lang.Nullable;

/**
 * Taken from Brave. The idea is such that you can operate on a {@link TraceContext}
 * instead of a Span. Brave will not create a span (thus won't report it) however you will
 * have span data in thread local.
 */
public interface CurrentTraceContext {

	@Nullable
	TraceContext get();

	CurrentTraceContext.Scope newScope(@Nullable TraceContext context);

	CurrentTraceContext.Scope maybeScope(@Nullable TraceContext context);

	interface Scope extends Closeable {

		@Override
		void close();

	}

}
