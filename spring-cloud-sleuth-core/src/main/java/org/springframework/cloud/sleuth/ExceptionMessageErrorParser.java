/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth;

import brave.SpanCustomizer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * {@link ErrorParser} that sets the error tag for an exportable span.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.1
 */
public class ExceptionMessageErrorParser implements ErrorParser {

	private static final Log log = LogFactory.getLog(ExceptionMessageErrorParser.class);

	@Override
	public void parseErrorTags(SpanCustomizer span, Throwable error) {
		if (span != null && error != null) {
			String errorMsg = getExceptionMessage(error);
			if (log.isDebugEnabled()) {
				log.debug("Adding an error tag [" + errorMsg + "] to span " + span);
			}
			span.tag("error", errorMsg);
		}
	}

	private String getExceptionMessage(Throwable e) {
		return e.getMessage() != null ? e.getMessage() : e.toString();
	}
}
