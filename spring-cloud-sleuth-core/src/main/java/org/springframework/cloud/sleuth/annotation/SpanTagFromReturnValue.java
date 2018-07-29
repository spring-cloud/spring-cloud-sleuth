package org.springframework.cloud.sleuth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * This annotation allows to add a tag to the current span based on the return value of the annotated method.
 *
 * There are the following 4 different ways specify the value of the resulting tag, in order of precedence:
 *
 *  <ul>
 *      <li>try with the {@link TagValueResolver} bean</li>
 *      <li>if the value of the bean wasn't set, try to evaluate a SPEL expression</li>
 *      <li>if there’s no SPEL expression just return a {@code toString()} value of the return value if it is not {@code null}</li>
 *      <li>return the empty string otherwise</li>
 *  </ul>
 *
 * If the method has return type {@code void}, the tag representing the (non-existent) return value will not be created.
 *
 * Note: This annotation and its use is fundamentally equivalent to {@link SpanTag}, with the only difference that it
 * applies to return values rather than method parameters. This separate annotation has been created instead of
 * allowing {@link SpanTag} to be also applied at {@link ElementType#METHOD} to avoid mistaken tagging of return values
 * instead of parameters and vice-versa.
 *
 * @author Michele Mancioppi
 * @since 2.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(value = { ElementType.METHOD })
public @interface SpanTagFromReturnValue {

	/**
	 * The name of the key of the tag which should be created.
	 */
	@AliasFor("key")
	String value() default "";

	/**
	 * The name of the key of the tag which should be created.
	 */
	@AliasFor("value")
	String key() default "";

	/**
	 * Execute this SPEL expression to calculate the tag value. Will be analyzed if no value of the
	 * {@link SpanTagFromReturnValue#resolver()} was set.
	 */
	String expression() default "";

	/**
	 * Use this bean to resolve the tag value. Has the highest precedence.
	 */
	Class<? extends TagValueResolver> resolver() default NoOpTagValueResolver.class;

}
