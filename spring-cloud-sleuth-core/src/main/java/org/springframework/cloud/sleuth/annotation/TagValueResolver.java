package org.springframework.cloud.sleuth.annotation;

/**
 * Resolves the tag value for the given parameter.
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
public interface TagValueResolver {

	/**
	 * Returns the tag value for the given parameter
	 *
	 * @param parameter - parameter annotated with {@link SpanTag}
	 * @return the value of the tag
	 */
	String resolve(Object parameter);
	
}
