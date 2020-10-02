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

package org.springframework.cloud.sleuth.api.http;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.lang.Nullable;

public interface Response {

	/** The remote {@link Span.Kind} describing the direction and type of the response. */
	Span.Kind spanKind();

	@Nullable
	Request request();

	@Nullable
	Throwable error();

	/**
	 * Returns the underlying response object or {@code null} if there is none. Here are
	 * some response objects: {@code org.apache.http.HttpResponse},
	 * {@code org.apache.dubbo.rpc.Result}, {@code
	 * org.apache.kafka.clients.producer.RecordMetadata}.
	 *
	 * <p>
	 * Note: Some implementations are composed of multiple types, such as a response and
	 * matched route of the server. Moreover, an implementation may change the type
	 * returned due to refactoring. Unless you control the implementation, cast carefully
	 * (ex using {@code
	 * instanceof}) instead of presuming a specific type will always be returned.
	 *
	 * @since 5.10
	 */
	Object unwrap();

}
