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

package org.springframework.cloud.sleuth;

import java.util.Collection;
import java.util.LinkedHashSet;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Well-known {@link brave.Span#tag(String, String) span tag}
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
 *
 * @since 1.0.0
 */
@ConfigurationProperties("spring.sleuth.keys")
public class TraceKeys {

	private Http http = new Http();

	private Message message = new Message();

	private Hystrix hystrix = new Hystrix();

	private Async async = new Async();

	private Mvc mvc = new Mvc();

	public Http getHttp() {
		return this.http;
	}

	public Message getMessage() {
		return this.message;
	}

	public Hystrix getHystrix() {
		return this.hystrix;
	}

	public Async getAsync() {
		return this.async;
	}

	public Mvc getMvc() {
		return this.mvc;
	}

	public void setHttp(Http http) {
		this.http = http;
	}

	public void setMessage(Message message) {
		this.message = message;
	}

	public void setHystrix(Hystrix hystrix) {
		this.hystrix = hystrix;
	}

	public void setAsync(Async async) {
		this.async = async;
	}

	public void setMvc(Mvc mvc) {
		this.mvc = mvc;
	}

	public static class Message {

		private Payload payload = new Payload();

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

		public static class Payload {
			/**
			 * An estimate of the size of the payload if available.
			 */
			private String size = "message/payload-size";
			/**
			 * The type of the payload.
			 */
			private String type = "message/payload-type";

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
	}

	/**
	 * Trace keys related to Hystrix processing
	 */
	public static class Hystrix {

		/**
		 * Prefix for header names if they are added as tags.
		 */
		private String prefix = "";

		/**
		 * Name of the command key. Describes the name for the given command.
		 * A key to represent a {@link com.netflix.hystrix.HystrixCommand} for
		 * monitoring, circuit-breakers, metrics publishing, caching and other such uses.
		 *
		 * @see com.netflix.hystrix.HystrixCommandKey
		 */
		private String commandKey = "commandKey";

		/**
		 * Name of the command group. Hystrix uses the command group key to group
		 * together commands such as for reporting, alerting, dashboards,
		 * or team/library ownership.
		 *
		 * @see com.netflix.hystrix.HystrixCommandGroupKey
		 */
		private String commandGroup = "commandGroup";

		/**
		 * Name of the thread pool key. The thread-pool key represents a {@link com.netflix.hystrix.HystrixThreadPool}
		 * for monitoring, metrics publishing, caching, and other such uses. A {@link com.netflix.hystrix.HystrixCommand}
		 * is associated with a single {@link com.netflix.hystrix.HystrixThreadPool} as
		 * retrieved by the {@link com.netflix.hystrix.HystrixThreadPoolKey} injected into it,
		 * or it defaults to one created using the {@link com.netflix.hystrix.HystrixCommandGroupKey}
		 * it is created with.
		 *
		 * @see com.netflix.hystrix.HystrixThreadPoolKey
		 */
		private String threadPoolKey = "threadPoolKey";

		public String getPrefix() {
			return this.prefix;
		}

		public String getCommandKey() {
			return this.commandKey;
		}

		public String getCommandGroup() {
			return this.commandGroup;
		}

		public String getThreadPoolKey() {
			return this.threadPoolKey;
		}

		public void setPrefix(String prefix) {
			this.prefix = prefix;
		}

		public void setCommandKey(String commandKey) {
			this.commandKey = commandKey;
		}

		public void setCommandGroup(String commandGroup) {
			this.commandGroup = commandGroup;
		}

		public void setThreadPoolKey(String threadPoolKey) {
			this.threadPoolKey = threadPoolKey;
		}
	}

	/**
	 * Trace keys related to async processing
	 */
	public static class Async {

		/**
		 * Prefix for header names if they are added as tags.
		 */
		private String prefix = "";

		/**
		 * Name of the thread that executed the async method
		 *
		 * @see org.springframework.scheduling.annotation.Async
		 */
		private String threadNameKey = "thread";

		/**
		 * Simple name of the class with a method annotated with {@code @Async}
		 * from which the asynchronous process started
		 *
		 * @see org.springframework.scheduling.annotation.Async
		 */
		private String classNameKey = "class";

		/**
		 * Name of the method annotated with {@code @Async}
		 *
		 * @see org.springframework.scheduling.annotation.Async
		 */
		private String methodNameKey = "method";

		public String getPrefix() {
			return this.prefix;
		}

		public String getThreadNameKey() {
			return this.threadNameKey;
		}

		public String getClassNameKey() {
			return this.classNameKey;
		}

		public String getMethodNameKey() {
			return this.methodNameKey;
		}

		public void setPrefix(String prefix) {
			this.prefix = prefix;
		}

		public void setThreadNameKey(String threadNameKey) {
			this.threadNameKey = threadNameKey;
		}

		public void setClassNameKey(String classNameKey) {
			this.classNameKey = classNameKey;
		}

		public void setMethodNameKey(String methodNameKey) {
			this.methodNameKey = methodNameKey;
		}
	}

	/**
	 * Trace keys related to MVC controller tags
	 */
	public static class Mvc {

		/**
		 * The lower case, hyphen delimited name of the class that processes the request.
		 * Ex. class named "BookController" will result in "book-controller" tag value.
		 */
		private String controllerClass = "mvc.controller.class";

		/**
		 * The lower case, hyphen delimited name of the class that processes the request.
		 * Ex. method named "listOfBooks" will result in "list-of-books" tag value.
		 */
		private String controllerMethod = "mvc.controller.method";

		public String getControllerClass() {
			return this.controllerClass;
		}

		public void setControllerClass(String controllerClass) {
			this.controllerClass = controllerClass;
		}

		public String getControllerMethod() {
			return this.controllerMethod;
		}

		public void setControllerMethod(String controllerMethod) {
			this.controllerMethod = controllerMethod;
		}
	}

}
