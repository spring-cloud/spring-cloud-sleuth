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
 * Use this to control the response data recorded for an
 * {@link TraceContext#sampledLocal() sampled HTTP client or server span}.
 *
 * <p>
 * Here's an example that adds all HTTP status codes, not just the error ones. <pre>{@code
 * httpTracing = httpTracing.toBuilder()
 *   .clientResponseParser((response, context, span) -> {
 *     HttpResponseParser.DEFAULT.parse(response, context, span);
 *     HttpTags.STATUS_CODE.tag(response, context, span);
 *   }).build();
 * }</pre>
 *
 * <p>
 * <em>Note</em>: This type is safe to implement as a lambda, or use as a method reference
 * as it is effectively a {@code FunctionalInterface}. It isn't annotated as such because
 * the project has a minimum Java language level 6.
 *
 * @see HttpRequestParser
 * @since 5.10
 */
public interface HttpResponseParser {

	void parse(HttpResponse response, TraceContext context, SpanCustomizer span);

}
