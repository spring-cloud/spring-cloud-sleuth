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

package org.springframework.cloud.sleuth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * There are 3 different ways to add tags to a span. All of them are controlled by the annotation values.
 * Precedence is:
 *
 *  <ul>
 *      <li>try with the {@link TagValueResolver} bean</li>
 *      <li>if the value of the bean wasn't set, try to evaluate a SPEL expression</li>
 *      <li>if there’s no SPEL expression just return a {@code toString()} value of the parameter</li>
 *  </ul>
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(value = { ElementType.PARAMETER })
public @interface SpanTag {

	/**
	 * The name of the key of the tag which should be created.
	 */
	String value() default "";

	/**
	 * The name of the key of the tag which should be created.
	 */
	@AliasFor("value")
	String key() default "";

	/**
	 * Execute this SPEL expression to calculate the tag value. Will be analyzed if no value of the
	 * {@link SpanTag#resolver()} was set.
	 */
	String expression() default "";

	/**
	 * Use this bean to resolve the tag value. Has the highest precedence.
	 */
	Class<? extends TagValueResolver> resolver() default NoOpTagValueResolver.class;

}
