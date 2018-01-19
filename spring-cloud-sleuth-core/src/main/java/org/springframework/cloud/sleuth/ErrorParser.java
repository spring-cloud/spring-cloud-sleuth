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
