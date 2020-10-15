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

public interface CurrentTraceContext {

	/** Returns the current span in scope or null if there isn't one. */
	@Nullable
	TraceContext get();

	/**
	 * Sets the current span in scope until the returned object is closed. It is a
	 * programming error to drop or never close the result. Using try-with-resources is
	 * preferred for this reason.
	 * @param context span to place into scope or null to clear the scope
	 */
	CurrentTraceContext.Scope newScope(@Nullable TraceContext context);

	CurrentTraceContext.Scope maybeScope(@Nullable TraceContext context);

	interface Scope extends Closeable {

		@Override
		void close();

	}

}
