/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth;

/**
 * Adopted from <a href=
 * "https://github.com/opentracing/opentracing-java/blob/master/opentracing/src/main/java/opentracing/Tracer.java">
 * </a>OpenTracing</a>
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public interface SpanInjector<T> {
	/**
	 * Takes two arguments:
	 * <ul>
	 * <li>a Span instance, and</li>
	 * <li>a “carrier” object in which to inject that Span for cross-process propagation.
	 * </li>
	 * </ul>
	 *
	 * A “carrier” object is some sort of http or rpc envelope, for example HeaderGroup
	 * (from Apache HttpComponents).
	 *
	 * Attempting to inject to a carrier that has been registered/configured to this
	 * Tracer will result in a IllegalStateException.
	 */
	void inject(Span span, T carrier);
}
