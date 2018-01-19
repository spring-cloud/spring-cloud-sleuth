package org.springframework.cloud.sleuth.log;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for slf4j
 *
 * @author Arthur Gavlyukovskiy
 * @since 1.0.12
 */
@ConfigurationProperties("spring.sleuth.log.slf4j")
public class SleuthSlf4jProperties {

	/**
	 * Enable a {@link Slf4jCurrentTraceContext} that prints tracing information in the logs.
	 */
	private boolean enabled = true;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
