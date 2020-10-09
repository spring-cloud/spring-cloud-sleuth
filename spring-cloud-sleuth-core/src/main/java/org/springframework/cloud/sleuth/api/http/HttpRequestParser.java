/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.cloud.sleuth.api.http;

import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.TraceContext;

/**
 * Use this to control the request data recorded for an {@link TraceContext#sampledLocal()
 * sampled HTTP client or server span}.
 *
 * <p>
 * Here's an example that changes the span name and records the HTTP url instead of the
 * path. <pre>{@code
 * httpTracing = httpTracing.toBuilder()
 *   .clientRequestParser((req, context, span) -> {
 *     String method = req.method();
 *     if (method != null) span.name(method);
 *     HttpTags.URL.tag(req, context, span); // the whole url, not just the path
 *   }).build();
 * }</pre>
 *
 * <p>
 * <em>Note</em>: This type is safe to implement as a lambda, or use as a method reference
 * as it is effectively a {@code FunctionalInterface}. It isn't annotated as such because
 * the project has a minimum Java language level 6.
 *
 * @see HttpResponseParser
 * @since 5.10
 */
// @FunctionalInterface, except Java language level 6. Do not add methods as it will break
// API!
public interface HttpRequestParser {

	/**
	 * Implement to choose what data from the http request are parsed into the span
	 * representing it.
	 */
	void parse(HttpRequest request, TraceContext context, SpanCustomizer span);

}
