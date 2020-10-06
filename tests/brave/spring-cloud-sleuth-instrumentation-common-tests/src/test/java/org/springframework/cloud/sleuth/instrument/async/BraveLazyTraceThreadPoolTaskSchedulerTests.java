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

package org.springframework.cloud.sleuth.instrument.async;

import org.springframework.cloud.sleuth.brave.BraveTestTracing;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TracerAware;

public class BraveLazyTraceThreadPoolTaskSchedulerTests extends LazyTraceThreadPoolTaskSchedulerTests {

	BraveTestTracing braveTestTracing = new BraveTestTracing();

	@Override
	public TracerAware tracing() {
		return this.braveTestTracing;
	}

	@Override
	public TestSpanHandler handler() {
		return this.braveTestTracing.handler();
	}

}
