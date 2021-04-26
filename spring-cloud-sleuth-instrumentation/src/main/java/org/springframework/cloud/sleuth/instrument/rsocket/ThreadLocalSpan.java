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

package org.springframework.cloud.sleuth.instrument.rsocket;

import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingDeque;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// Consider moving it out directly to Sleuth cause it's a copy from SI
class ThreadLocalSpan {

	private static final Log log = LogFactory.getLog(ThreadLocalSpan.class);

	final ThreadLocal<SpanAndScope> threadLocalSpan = new ThreadLocal<>();

	final LinkedBlockingDeque<SpanAndScope> spans = new LinkedBlockingDeque<>();

	void set(SpanAndScope spanAndScope) {
		SpanAndScope scope = this.threadLocalSpan.get();
		if (scope != null) {
			this.spans.addFirst(scope);
		}
		this.threadLocalSpan.set(spanAndScope);
	}

	SpanAndScope get() {
		return this.threadLocalSpan.get();
	}

	void remove() {
		this.threadLocalSpan.remove();
		if (this.spans.isEmpty()) {
			return;
		}
		try {
			SpanAndScope span = this.spans.removeFirst();
			if (log.isDebugEnabled()) {
				log.debug("Took span [" + span + "] from thread local");
			}
			this.threadLocalSpan.set(span);
		}
		catch (NoSuchElementException ex) {
			if (log.isTraceEnabled()) {
				log.trace("Failed to remove a span from the queue", ex);
			}
		}
	}

}
