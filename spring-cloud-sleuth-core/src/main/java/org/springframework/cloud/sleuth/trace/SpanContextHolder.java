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

import java.lang.invoke.MethodHandles;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.core.NamedThreadLocal;

/**
 * @author Spencer Gibb
 */
public class SpanContextHolder {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private static final ThreadLocal<Span> CURRENT_SPAN = new NamedThreadLocal<>("Trace Context");

	public static Span getCurrentSpan() {
		return isTracing() ? CURRENT_SPAN.get() : null;
	}

	public static void setCurrentSpan(Span span) {
		// backwards compatibility
		if (span == null) {
			CURRENT_SPAN.remove();
			return;
		}
		if (log.isTraceEnabled()) {
			log.trace("Setting current span " + span);
		}
		CURRENT_SPAN.set(span);
	}

	public static void removeCurrentSpan() {
		CURRENT_SPAN.remove();
	}

	public static boolean isTracing() {
		return CURRENT_SPAN.get() != null;
	}
}
