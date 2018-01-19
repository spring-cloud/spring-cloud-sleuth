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
 * Allows to create a new span around a public method. The new span
 * will be either a child of an existing span if a trace is already in progress
 * or a new span will be created if there was no previous trace.
 * <p>
 * Method parameters can be annotated with {@link SpanTag}, which will end
 * in adding the parameter value as a tag value to the span. The tag key will be
 * the value of the {@code key} annotation from {@link SpanTag}.
 *
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(value = { ElementType.METHOD })
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
