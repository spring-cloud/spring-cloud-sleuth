package org.springframework.cloud.sleuth.instrument.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for {@link org.springframework.scheduling.annotation.Scheduled} tracing
 *
 * @author Arthur Gavlyukovskiy
 * @since 1.0.12
 */
@ConfigurationProperties("spring.sleuth.scheduled")
public class SleuthSchedulingProperties {

	/**
	 * Enable tracing for {@link org.springframework.scheduling.annotation.Scheduled}.
	 */
	private boolean enabled = true;

	/**
	 * Pattern for the fully qualified name of a class that should be skipped.
	 */
	private String skipPattern = "";

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
}
