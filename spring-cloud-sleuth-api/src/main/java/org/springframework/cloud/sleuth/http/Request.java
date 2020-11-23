/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.http;

import java.util.Collection;

import org.springframework.cloud.sleuth.Span;

/**
 * This API is taken from OpenZipkin Brave.
 *
 * Abstract request type used for parsing and sampling.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface Request {

	/**
	 * @return list of header names.
	 */
	Collection<String> headerNames();

	/**
	 * @return The remote {@link Span.Kind} describing the direction and type of the
	 * request.
	 */
	Span.Kind spanKind();

	/**
	 * @return the underlying request object or {@code null} if there is none.
	 */
	Object unwrap();

}
