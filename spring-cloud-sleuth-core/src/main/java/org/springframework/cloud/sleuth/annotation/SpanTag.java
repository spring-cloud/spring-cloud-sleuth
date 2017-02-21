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
 * A method parameter which is annotated with this annotation,
 * will be added as a tag. The name will be the {@code value} property,
 * using the {@code toString()} representation of the parameter as tag-value.
 * 
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(value = { ElementType.PARAMETER })
public @interface SpanTag {

	/**
	 * The name of the key of the tag which should be created
	 */
	@AliasFor("key")
	String value() default "";

	/**
	 * The name of the key of the tag which should be created
	 */
	@AliasFor("value")
	String key() default "";

	/**
	 * Execute this SPEL expression to calculate the tag value
	 */
	String tagValueExpression() default "";

	/**
	 * Use this bean name to retrieve the tag value
	 */
	String tagValueResolverBeanName() default "";

}
