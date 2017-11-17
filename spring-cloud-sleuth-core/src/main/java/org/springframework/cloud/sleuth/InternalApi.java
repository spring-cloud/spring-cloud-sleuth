package org.springframework.cloud.sleuth;

/**
 * Internal API that can be changed any time so please do not use it!
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public class InternalApi {

	public static void renameSpan(Span span, String newName) {
		span.name = newName;
	}
}
