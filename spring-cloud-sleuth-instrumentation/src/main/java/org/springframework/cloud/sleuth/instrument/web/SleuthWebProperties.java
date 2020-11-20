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

package org.springframework.cloud.sleuth.instrument.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.core.Ordered;

/**
 * Configuration properties for web tracing.
 *
 * @author Arthur Gavlyukovskiy
 * @since 1.0.12
 */
@ConfigurationProperties("spring.sleuth.web")
public class SleuthWebProperties {

	/**
	 * If you register your filter before the {@link TraceWebFilter} then you will not
	 * have the tracing context passed for you out of the box. That means that e.g. your
	 * logs will not get correlated.
	 */
	public static final int TRACING_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

	/**
	 * Default set of skip patterns.
	 */
	public static final String DEFAULT_SKIP_PATTERN = "/api-docs.*|/swagger.*|"
			+ ".*\\.png|.*\\.css|.*\\.js|.*\\.html|/favicon.ico|/hystrix.stream";

	/**
	 * When true enables instrumentation for web applications.
	 */
	private boolean enabled = true;

	/**
	 * Pattern for URLs that should be skipped in tracing.
	 */
	private String skipPattern = DEFAULT_SKIP_PATTERN;

	/**
	 * Additional pattern for URLs that should be skipped in tracing. This will be
	 * appended to the {@link SleuthWebProperties#skipPattern}.
	 */
	private String additionalSkipPattern;

	/**
	 * Order in which the tracing filters should be registered.
	 */
	private int filterOrder = TRACING_FILTER_ORDER;

	/**
	 * If set to true, auto-configured skip patterns will be ignored.
	 */
	private boolean ignoreAutoConfiguredSkipPatterns = false;

	/**
	 * Properties related to HTTP clients.
	 */
	private Client client = new Client();

	/**
	 * Properties related to servlets.
	 */
	private Servlet servlet = new Servlet();

	public static String getDefaultSkipPattern() {
		return DEFAULT_SKIP_PATTERN;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getSkipPattern() {
		return this.skipPattern;
	}

	public void setSkipPattern(String skipPattern) {
		this.skipPattern = emptyToNull(skipPattern);
	}

	public String getAdditionalSkipPattern() {
		return this.additionalSkipPattern;
	}

	public void setAdditionalSkipPattern(String additionalSkipPattern) {
		this.additionalSkipPattern = emptyToNull(additionalSkipPattern);
	}

	public int getFilterOrder() {
		return this.filterOrder;
	}

	public void setFilterOrder(int filterOrder) {
		this.filterOrder = filterOrder;
	}

	public boolean isIgnoreAutoConfiguredSkipPatterns() {
		return ignoreAutoConfiguredSkipPatterns;
	}

	public void setIgnoreAutoConfiguredSkipPatterns(boolean ignoreAutoConfiguredSkipPatterns) {
		this.ignoreAutoConfiguredSkipPatterns = ignoreAutoConfiguredSkipPatterns;
	}

	public Client getClient() {
		return this.client;
	}

	public void setClient(Client client) {
		this.client = client;
	}

	public Servlet getServlet() {
		return this.servlet;
	}

	public void setServlet(Servlet servlet) {
		this.servlet = servlet;
	}

	static String emptyToNull(String skipPattern) {
		if (skipPattern != null && skipPattern.isEmpty()) {
			skipPattern = null; // otherwise this would skip paths named ""!
		}
		return skipPattern;
	}

	/**
	 * Web client properties.
	 *
	 * @author Marcin Grzejszczak
	 */
	public static class Client {

		/**
		 * Pattern for URLs that should be skipped in client side tracing.
		 */
		private String skipPattern;

		/**
		 * Enable interceptor injecting into
		 * {@link org.springframework.web.client.RestTemplate}.
		 */
		private boolean enabled = true;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getSkipPattern() {
			return this.skipPattern;
		}

		public void setSkipPattern(String skipPattern) {
			this.skipPattern = emptyToNull(skipPattern);
		}

	}

	/**
	 * Web client properties.
	 *
	 * @author Marcin Grzejszczak
	 */
	public static class Servlet {

		/**
		 * Enable servlet instrumentation.
		 */
		private boolean enabled = true;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

	/**
	 * Async computing properties.
	 *
	 * @author Marcin Grzejszczak
	 */
	public static class Async {

		@NestedConfigurationProperty
		private AsyncClient client;

		public AsyncClient getClient() {
			return this.client;
		}

		public void setClient(AsyncClient client) {
			this.client = client;
		}

	}

	/**
	 * Async client properties.
	 *
	 * @author Marcin Grzejszczak
	 */
	public static class AsyncClient {

		/**
		 * Enable span information propagation for
		 * {@link org.springframework.http.client.AsyncClientHttpRequestFactory}.
		 */
		private boolean enabled;

		@NestedConfigurationProperty
		private Template template;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public Template getTemplate() {
			return this.template;
		}

		public void setTemplate(Template template) {
			this.template = template;
		}

	}

	/**
	 * Async Rest Template properties.
	 *
	 * @author Marcin Grzejszczak
	 */
	public static class Template {

		/**
		 * Enable span information propagation for
		 * {@link org.springframework.web.client.AsyncRestTemplate}.
		 */
		private boolean enabled;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

}
