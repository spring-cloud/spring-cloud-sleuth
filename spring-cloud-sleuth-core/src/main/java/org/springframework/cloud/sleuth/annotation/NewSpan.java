/*
 * Copyright 2013-2016 the original author or authors.
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
 * Allows to create a new span around a public method or a class.
 * <p>
 * For each public method in an annotated class, or self annotated method,
 * a new {@link org.springframework.cloud.sleuth.Span} will be created.
 * Method parameters can be annotated with {@link SpanTag}, which will
 * in adding the parameter value as a tag to the span.
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(value = { ElementType.METHOD, ElementType.TYPE })
public @interface NewSpan {

	/**
	 * The name of the span which will be created. Default is the annotated method's name separated by hyphens.
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * The name of the span which will be created. Default is the annotated method's name separated by hyphens.
	 */
	@AliasFor("name")
	String value() default "";

}
