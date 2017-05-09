package org.springframework.cloud.sleuth;

/**
 * Contract for parsing errors. For the given exception will produce a message
 * that will be then added as a {@link Span#SPAN_ERROR_TAG_NAME} tag to the Span.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.1
 */
public interface ErrorParser {

	/**
	 * Returns a {@link String} representation of a {@link Throwable}
	 */
	String parseError(Throwable error);
}
