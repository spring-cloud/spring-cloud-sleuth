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
public class TraceKeys {
	private Http http = new Http();

	private Message message = new Message();

	public TraceKeys() {
	}

	public Http getHttp() {
		return this.http;
	}

	public Message getMessage() {
		return this.message;
	}

	public void setHttp(Http http) {
		this.http = http;
	}

	public void setMessage(Message message) {
		this.message = message;
	}

	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof TraceKeys))
			return false;
		final TraceKeys other = (TraceKeys) o;
		if (!other.canEqual((Object) this))
			return false;
		final Object this$http = this.http;
		final Object other$http = other.http;
		if (this$http == null ? other$http != null : !this$http.equals(other$http))
			return false;
		final Object this$message = this.message;
		final Object other$message = other.message;
		if (this$message == null ?
				other$message != null :
				!this$message.equals(other$message))
			return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $http = this.http;
		result = result * PRIME + ($http == null ? 0 : $http.hashCode());
		final Object $message = this.message;
		result = result * PRIME + ($message == null ? 0 : $message.hashCode());
		return result;
	}

	protected boolean canEqual(Object other) {
		return other instanceof TraceKeys;
	}

	public String toString() {
		return "org.springframework.cloud.sleuth.instrument.TraceKeys(http=" + this.http
				+ ", message=" + this.message + ")";
	}

	public static class Message {

		private Payload payload = new Payload();

		public Message() {
		}

		public Payload getPayload() {
			return this.payload;
		}

		public String getPrefix() {
			return this.prefix;
		}

		public Collection<String> getHeaders() {
			return this.headers;
		}

		public void setPayload(Payload payload) {
			this.payload = payload;
		}

		public void setPrefix(String prefix) {
			this.prefix = prefix;
		}

		public void setHeaders(Collection<String> headers) {
			this.headers = headers;
		}

		public boolean equals(Object o) {
			if (o == this)
				return true;
			if (!(o instanceof Message))
				return false;
			final Message other = (Message) o;
			if (!other.canEqual((Object) this))
				return false;
			final Object this$payload = this.payload;
			final Object other$payload = other.payload;
			if (this$payload == null ?
					other$payload != null :
					!this$payload.equals(other$payload))
				return false;
			final Object this$prefix = this.prefix;
			final Object other$prefix = other.prefix;
			if (this$prefix == null ?
					other$prefix != null :
					!this$prefix.equals(other$prefix))
				return false;
			final Object this$headers = this.headers;
			final Object other$headers = other.headers;
			if (this$headers == null ?
					other$headers != null :
					!this$headers.equals(other$headers))
				return false;
			return true;
		}

		public int hashCode() {
			final int PRIME = 59;
			int result = 1;
			final Object $payload = this.payload;
			result = result * PRIME + ($payload == null ? 0 : $payload.hashCode());
			final Object $prefix = this.prefix;
			result = result * PRIME + ($prefix == null ? 0 : $prefix.hashCode());
			final Object $headers = this.headers;
			result = result * PRIME + ($headers == null ? 0 : $headers.hashCode());
			return result;
		}

		protected boolean canEqual(Object other) {
			return other instanceof Message;
		}

		public String toString() {
			return "org.springframework.cloud.sleuth.instrument.TraceKeys.Message(payload="
					+ this.payload + ", prefix=" + this.prefix + ", headers="
					+ this.headers + ")";
		}

		public static class Payload {
			/**
			 * An estimate of the size of the payload if available.
			 */
			private String size = "message/payload-size";
			/**
			 * The type of the payload.
			 */
			private String type = "message/payload-type";

			public Payload() {
			}

			public String getSize() {
				return this.size;
			}

			public String getType() {
				return this.type;
			}

			public void setSize(String size) {
				this.size = size;
			}

			public void setType(String type) {
				this.type = type;
			}

			public boolean equals(Object o) {
				if (o == this)
					return true;
				if (!(o instanceof Payload))
					return false;
				final Payload other = (Payload) o;
				if (!other.canEqual((Object) this))
					return false;
				final Object this$size = this.size;
				final Object other$size = other.size;
				if (this$size == null ?
						other$size != null :
						!this$size.equals(other$size))
					return false;
				final Object this$type = this.type;
				final Object other$type = other.type;
				if (this$type == null ?
						other$type != null :
						!this$type.equals(other$type))
					return false;
				return true;
			}

			public int hashCode() {
				final int PRIME = 59;
				int result = 1;
				final Object $size = this.size;
				result = result * PRIME + ($size == null ? 0 : $size.hashCode());
				final Object $type = this.type;
				result = result * PRIME + ($type == null ? 0 : $type.hashCode());
				return result;
			}

			protected boolean canEqual(Object other) {
				return other instanceof Payload;
			}

			public String toString() {
				return "org.springframework.cloud.sleuth.instrument.TraceKeys.Message.Payload(size="
						+ this.size + ", type=" + this.type + ")";
			}
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

		public Http() {
		}

		public String getHost() {
			return this.host;
		}

		public String getMethod() {
			return this.method;
		}

		public String getPath() {
			return this.path;
		}

		public String getUrl() {
			return this.url;
		}

		public String getStatusCode() {
			return this.statusCode;
		}

		public String getRequestSize() {
			return this.requestSize;
		}

		public String getResponseSize() {
			return this.responseSize;
		}

		public String getPrefix() {
			return this.prefix;
		}

		public Collection<String> getHeaders() {
			return this.headers;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public void setMethod(String method) {
			this.method = method;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public void setStatusCode(String statusCode) {
			this.statusCode = statusCode;
		}

		public void setRequestSize(String requestSize) {
			this.requestSize = requestSize;
		}

		public void setResponseSize(String responseSize) {
			this.responseSize = responseSize;
		}

		public void setPrefix(String prefix) {
			this.prefix = prefix;
		}

		public void setHeaders(Collection<String> headers) {
			this.headers = headers;
		}

		public boolean equals(Object o) {
			if (o == this)
				return true;
			if (!(o instanceof Http))
				return false;
			final Http other = (Http) o;
			if (!other.canEqual((Object) this))
				return false;
			final Object this$host = this.host;
			final Object other$host = other.host;
			if (this$host == null ? other$host != null : !this$host.equals(other$host))
				return false;
			final Object this$method = this.method;
			final Object other$method = other.method;
			if (this$method == null ?
					other$method != null :
					!this$method.equals(other$method))
				return false;
			final Object this$path = this.path;
			final Object other$path = other.path;
			if (this$path == null ? other$path != null : !this$path.equals(other$path))
				return false;
			final Object this$url = this.url;
			final Object other$url = other.url;
			if (this$url == null ? other$url != null : !this$url.equals(other$url))
				return false;
			final Object this$statusCode = this.statusCode;
			final Object other$statusCode = other.statusCode;
			if (this$statusCode == null ?
					other$statusCode != null :
					!this$statusCode.equals(other$statusCode))
				return false;
			final Object this$requestSize = this.requestSize;
			final Object other$requestSize = other.requestSize;
			if (this$requestSize == null ?
					other$requestSize != null :
					!this$requestSize.equals(other$requestSize))
				return false;
			final Object this$responseSize = this.responseSize;
			final Object other$responseSize = other.responseSize;
			if (this$responseSize == null ?
					other$responseSize != null :
					!this$responseSize.equals(other$responseSize))
				return false;
			final Object this$prefix = this.prefix;
			final Object other$prefix = other.prefix;
			if (this$prefix == null ?
					other$prefix != null :
					!this$prefix.equals(other$prefix))
				return false;
			final Object this$headers = this.headers;
			final Object other$headers = other.headers;
			if (this$headers == null ?
					other$headers != null :
					!this$headers.equals(other$headers))
				return false;
			return true;
		}

		public int hashCode() {
			final int PRIME = 59;
			int result = 1;
			final Object $host = this.host;
			result = result * PRIME + ($host == null ? 0 : $host.hashCode());
			final Object $method = this.method;
			result = result * PRIME + ($method == null ? 0 : $method.hashCode());
			final Object $path = this.path;
			result = result * PRIME + ($path == null ? 0 : $path.hashCode());
			final Object $url = this.url;
			result = result * PRIME + ($url == null ? 0 : $url.hashCode());
			final Object $statusCode = this.statusCode;
			result = result * PRIME + ($statusCode == null ? 0 : $statusCode.hashCode());
			final Object $requestSize = this.requestSize;
			result =
					result * PRIME + ($requestSize == null ? 0 : $requestSize.hashCode());
			final Object $responseSize = this.responseSize;
			result = result * PRIME + ($responseSize == null ?
					0 :
					$responseSize.hashCode());
			final Object $prefix = this.prefix;
			result = result * PRIME + ($prefix == null ? 0 : $prefix.hashCode());
			final Object $headers = this.headers;
			result = result * PRIME + ($headers == null ? 0 : $headers.hashCode());
			return result;
		}

		protected boolean canEqual(Object other) {
			return other instanceof Http;
		}

		public String toString() {
			return "org.springframework.cloud.sleuth.instrument.TraceKeys.Http(host="
					+ this.host + ", method=" + this.method + ", path=" + this.path
					+ ", url=" + this.url + ", statusCode=" + this.statusCode
					+ ", requestSize=" + this.requestSize + ", responseSize="
					+ this.responseSize + ", prefix=" + this.prefix + ", headers="
					+ this.headers + ")";
		}
	}

}
