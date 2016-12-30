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
	 * Enable a {@link Slf4jSpanLogger} that prints tracing information in the logs.
	 */
	private boolean enabled = true;

	/**
	 * Name pattern for which span should not be printed in the logs.
	 */
	private String nameSkipPattern = "";

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getNameSkipPattern() {
		return this.nameSkipPattern;
	}

	public void setNameSkipPattern(String nameSkipPattern) {
		this.nameSkipPattern = nameSkipPattern;
	}
}
