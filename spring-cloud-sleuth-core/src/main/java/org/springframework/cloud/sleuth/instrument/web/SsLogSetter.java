/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import java.lang.invoke.MethodHandles;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;

/**
 * Utility class to set SS log if it wasn't already set
 *
 * @author Marcin Grzejszczak
 */
class SsLogSetter {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	static void annotateWithServerSendIfLogIsNotAlreadyPresent(Span span) {
		if (span == null) {
			return;
		}
		for (org.springframework.cloud.sleuth.Log log1 : span.logs()) {
			if (Span.SERVER_SEND.equals(log1.getEvent())) {
				if (log.isTraceEnabled()) {
					log.trace("Span was already annotated with SS, will not do it again");
				}
				return;
			}
		}
		if (log.isTraceEnabled()) {
			log.trace("Will set SS on the span");
		}
		span.logEvent(Span.SERVER_SEND);
	}
}
