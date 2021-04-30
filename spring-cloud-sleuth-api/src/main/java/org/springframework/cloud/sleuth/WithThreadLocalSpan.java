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

package org.springframework.cloud.sleuth;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;

/**
 * Represents a {@link Span} stored in thread local.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public interface WithThreadLocalSpan {

	/**
	 * Logger.
	 */
	Log log = LogFactory.getLog(WithThreadLocalSpan.class);

	/**
	 * Sets the span in thread local scope.
	 * @param span span to put in thread local
	 */
	default void setSpanInScope(Span span) {
		getThreadLocalSpan().set(span);
		if (log.isDebugEnabled()) {
			log.debug("Put span in scope " + span);
		}
	}

	/**
	 * Finishes the thread local span.
	 * @param error potential error to be stored in span
	 */
	default void finishSpan(@Nullable Throwable error) {
		SpanAndScope spanAndScope = takeSpanFromThreadLocal();
		if (spanAndScope == null) {
			return;
		}
		Span span = spanAndScope.getSpan();
		Tracer.SpanInScope scope = spanAndScope.getScope();
		if (span.isNoop()) {
			if (log.isDebugEnabled()) {
				log.debug("Span " + span + " is noop - will stope the scope");
			}
			scope.close();
			return;
		}
		if (error != null) { // an error occurred, adding error to span
			span.error(error);
		}
		if (log.isDebugEnabled()) {
			log.debug("Will finish the span and its corresponding scope " + span);
		}
		span.end();
		scope.close();
	}

	/**
	 * Takes a span from thread local and restores the previous one if present.
	 * @return span from a thread local span
	 */
	default SpanAndScope takeSpanFromThreadLocal() {
		SpanAndScope span = getThreadLocalSpan().get();
		if (log.isDebugEnabled()) {
			log.debug("Took span [" + span + "] from thread local");
		}
		getThreadLocalSpan().remove();
		return span;
	}

	ThreadLocalSpan getThreadLocalSpan();

}
