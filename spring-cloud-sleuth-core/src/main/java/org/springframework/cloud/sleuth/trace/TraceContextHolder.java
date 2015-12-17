/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.trace;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.core.NamedThreadLocal;

import lombok.extern.apachecommons.CommonsLog;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class TraceContextHolder {

	private static final ThreadLocal<Trace> currentTrace = new NamedThreadLocal<>("Trace Context");

	public static Trace getCurrentTrace() {
		return currentTrace.get();
	}

	public static Span getCurrentSpan() {
		return isTracing() ? currentTrace.get().getSpan() : null;
	}

	public static void setCurrentTrace(Trace trace) {
		// backwards compatibility
		if (trace == null) {
			currentTrace.remove();
			return;
		}
		if (log.isTraceEnabled()) {
			log.trace("Setting current trace " + trace);
		}
		currentTrace.set(trace);
	}

	public static void removeCurrentTrace() {
		currentTrace.remove();
	}

	public static boolean isTracing() {
		return currentTrace.get() != null;
	}
}
