package org.springframework.cloud.sleuth.instrument.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for web tracing
 *
 * @author Arthur Gavlyukovskiy
 * @since 1.0.12
 */
@ConfigurationProperties("spring.sleuth.web")
public class SleuthWebProperties {

	public static final String DEFAULT_SKIP_PATTERN =
			"/api-docs.*|/autoconfig|/configprops|/dump|/health|/info|/metrics.*|/mappings|/trace|/swagger.*|.*\\.png|.*\\.css|.*\\.js|.*\\.html|/favicon.ico|/hystrix.stream|/application/.*";

	/**
	 * When true enables instrumentation for web applications
	 */
	private boolean enabled = true;

	/**
	 * Pattern for URLs that should be skipped in tracing
	 */
	private String skipPattern = DEFAULT_SKIP_PATTERN;

	private Client client;

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
		this.skipPattern = skipPattern;
	}

	public Client getClient() {
		return this.client;
	}

	public void setClient(Client client) {
		this.client = client;
	}

	public static class Client {

		/**
		 * Enable interceptor injecting into {@link org.springframework.web.client.RestTemplate}
		 */
		private boolean enabled = true;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}

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

	public static class AsyncClient {

		/**
		 * Enable span information propagation for {@link org.springframework.http.client.AsyncClientHttpRequestFactory}.
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

	public static class Template {

		/**
		 * Enable span information propagation for {@link org.springframework.web.client.AsyncRestTemplate}.
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
