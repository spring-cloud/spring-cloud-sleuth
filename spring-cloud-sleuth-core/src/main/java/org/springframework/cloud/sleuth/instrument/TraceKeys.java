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

package org.springframework.cloud.sleuth.instrument;

import java.util.Collection;
import java.util.LinkedHashSet;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Well-known {@link org.springframework.cloud.sleuth.Span#tag(String, String) span tag}
 * keys.
 *
 * <h3>Overhead of adding Trace Data</h3>
 *
 * Overhead is directly related to the size of trace data exported out of process.
 * Accordingly, it is better to tag what's important for latency troubleshooting, i.e. a
 * whitelist vs. collecting everything and filtering downstream. The keys listed here are
 * very common in tracing tools, and are considerate to the issue of overhead.
 *
 * <p>
 * When evaluating new keys, consider how much additional data it implies, and if that
 * data is critical to classifying, filtering or displaying traces. More data often means
 * larger systems, less retention, or a lower sample rate.
 *
 * <p>
 * For example, in zipkin, a thrift-encoded span with an "sr" annotation is 82 bytes plus
 * the size of its name and associated service. The maximum size of an HTTP cookie is 4096
 * bytes, roughly 50x that. Even if compression helps, if you aren't analyzing based on
 * cookies, storing them displaces resources that could be used for more traces.
 * Meanwhile, you have another system storing private data! The takeaway isn't never store
 * cookies, as there are valid cases for this. The takeaway is to be conscious about
 * what's you are storing.
 */
@ConfigurationProperties("spring.sleuth.keys")
@Data
public class TraceKeys {

	private Http http = new Http();

	private Message message = new Message();

	@Data
	public static class Message {

		private Payload payload = new Payload();

		@Data
		public static class Payload {
			/**
			 * An estimate of the size of the payload if available.
			 */
			private String size = "message/payload-size";
			/**
			 * The type of the payload.
			 */
			private String type = "message/payload-type";
		}

		/**
		 * Prefix for header names if they are added as tags.
		 */
		private String prefix = "message/";

		/**
		 * Additional headers that should be added as tags if they exist. If the header
		 * value is not a String it will be converted to a String using its toString()
		 * method.
		 */
		private Collection<String> headers = new LinkedHashSet<String>();

	}

	@Data
	public static class Http {

		/**
		 * The domain portion of the URL or host header. Example:
		 * "mybucket.s3.amazonaws.com". Used to filter by host as opposed to ip address.
		 */
		private String host = "http.host";

		/**
		 * The HTTP method, or verb, such as "GET" or "POST". Used to filter against an
		 * http route.
		 */
		private String method = "http.method";

		/**
		 * The absolute http path, without any query parameters. Example:
		 * "/objects/abcd-ff". Used to filter against an http route, portably with zipkin
		 * v1. In zipkin v1, only equals filters are supported. Dropping query parameters
		 * makes the number of distinct URIs less. For example, one can query for the same
		 * resource, regardless of signing parameters encoded in the query line. This does
		 * not reduce cardinality to a HTTP single route. For example, it is common to
		 * express a route as an http URI template like "/resource/{resource_id}". In
		 * systems where only equals queries are available, searching for
		 * {@code http.uri=/resource} won't match if the actual request was
		 * "/resource/abcd-ff". Historical note: This was commonly expressed as "http.uri"
		 * in zipkin, eventhough it was most often just a path.
		 */
		private String path = "http.path";

		/**
		 * The entire URL, including the scheme, host and query parameters if available.
		 * Ex.
		 * "https://mybucket.s3.amazonaws.com/objects/abcd-ff?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Algorithm=AWS4-HMAC-SHA256..."
		 * Combined with {@link #method}, you can understand the fully-qualified
		 * request line. This is optional as it may include private data or be of
		 * considerable length.
		 */
		private String url = "http.url";

		/**
		 * The HTTP response code, when not in 2xx range. Ex. "503" Used to filter for
		 * error status. 2xx range are not logged as success codes are less interesting
		 * for latency troubleshooting. Omitting saves at least 20 bytes per span.
		 */
		private String statusCode = "http.status_code";

		/**
		 * The size of the non-empty HTTP request body, in bytes. Ex. "16384"
		 *
		 * <p>Large uploads can exceed limits or contribute directly to latency.
		 */
		private String requestSize = "http.request.size";

		/**
		 * The size of the non-empty HTTP response body, in bytes. Ex. "16384"
		 *
		 * <p>Large downloads can exceed limits or contribute directly to latency.
		 */
		private String responseSize = "http.response.size";

		/**
		 * Prefix for header names if they are added as tags.
		 */
		private String prefix = "http.";

		/**
		 * Additional headers that should be added as tags if they exist. If the header
		 * value is multi-valued, the tag value will be a comma-separated, single-quoted
		 * list.
		 */
		private Collection<String> headers = new LinkedHashSet<String>();

	}

}
