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

package org.springframework.cloud.sleuth.api.propagation;

import java.util.List;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.lang.Nullable;

/**
 * Taken from Brave & OTel.
 */
public interface Propagator {

	List<String> fields();

	/**
	 * Injects the value downstream, for example as HTTP headers. The carrier may be null
	 * to facilitate calling this method with a lambda for the {@link Setter}, in which
	 * case that null will be passed to the {@link Setter} implementation.
	 * @param context the {@code Context} containing the value to be injected.
	 * @param carrier holds propagation fields. For example, an outgoing message or http
	 * request.
	 * @param setter invoked for each propagation key to add or remove.
	 * @param <C> carrier of propagation fields, such as an http request
	 * @since 0.1.0
	 */
	<C> void inject(TraceContext context, @Nullable C carrier, Setter<C> setter);

	/**
	 * Class that allows a {@code TextMapPropagator} to set propagated fields into a
	 * carrier.
	 *
	 * <p>
	 * {@code Setter} is stateless and allows to be saved as a constant to avoid runtime
	 * allocations.
	 *
	 * @param <C> carrier of propagation fields, such as an http request
	 * @since 0.1.0
	 */
	interface Setter<C> {

		/**
		 * Replaces a propagated field with the given value.
		 *
		 * <p>
		 * For example, a setter for an {@link java.net.HttpURLConnection} would be the
		 * method reference
		 * {@link java.net.HttpURLConnection#addRequestProperty(String, String)}
		 * @param carrier holds propagation fields. For example, an outgoing message or
		 * http request. To facilitate implementations as java lambdas, this parameter may
		 * be null.
		 * @param key the key of the field.
		 * @param value the value of the field.
		 * @since 0.1.0
		 */
		void set(@Nullable C carrier, String key, String value);

	}

	/**
	 * Extracts the value from upstream. For example, as http headers.
	 *
	 * <p>
	 * If the value could not be parsed, the underlying implementation will decide to set
	 * an object representing either an empty value, an invalid value, or a valid value.
	 * Implementation must not set {@code null}.
	 * @param carrier holds propagation fields. For example, an outgoing message or http
	 * request.
	 * @param getter invoked for each propagation key to get.
	 * @param <C> carrier of propagation fields, such as an http request.
	 * @return the {@code Context} containing the extracted value.
	 * @since 0.1.0
	 */
	<C> Span.Builder extract(C carrier, Getter<C> getter);

	/**
	 * Interface that allows a {@code TextMapPropagator} to read propagated fields from a
	 * carrier.
	 *
	 * <p>
	 * {@code Getter} is stateless and allows to be saved as a constant to avoid runtime
	 * allocations.
	 *
	 * @param <C> carrier of propagation fields, such as an http request.
	 * @since 0.1.0
	 */
	interface Getter<C> {

		/**
		 * Returns the first value of the given propagation {@code key} or returns
		 * {@code null}.
		 * @param carrier carrier of propagation fields, such as an http request.
		 * @param key the key of the field.
		 * @return the first value of the given propagation {@code key} or returns
		 * {@code null}.
		 * @since 0.1.0
		 */
		@Nullable
		String get(C carrier, String key);

	}

}
