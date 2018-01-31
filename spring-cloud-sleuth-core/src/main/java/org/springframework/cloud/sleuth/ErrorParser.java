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

/**
 * Contract for hooking into process of adding error response tags.
 * This interface is only called when an exception is thrown upon receiving a response.
 * (e.g. a response of 500 may not be an exception).
 *
 * @author Marcin Grzejszczak
 * @since 1.2.1
 */
public interface ErrorParser {

	/**
	 * Allows setting of tags when an exception was thrown when the response was received.
	 *
	 * @param span - current span in context
	 * @param error - error that was thrown upon receiving a response
	 */
	void parseErrorTags(SpanCustomizer span, Throwable error);
}
