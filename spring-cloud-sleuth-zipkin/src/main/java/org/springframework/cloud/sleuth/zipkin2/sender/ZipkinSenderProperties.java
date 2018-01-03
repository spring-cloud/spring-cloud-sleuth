package org.springframework.cloud.sleuth.zipkin2.sender;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties for Zipkin sender
 *
 * @author Marcin Grzejszczak
 * @since 1.3.1
 */
@ConfigurationProperties("spring.zipkin.sender")
public class ZipkinSenderProperties {

	/**
	 * Means of sending spans to Zipkin
	 */
	private SenderType type;

	public SenderType getType() {
		return this.type;
	}

	public void setType(SenderType type) {
		this.type = type;
	}

	public enum SenderType {
		RABBIT, KAFKA, WEB
	}
}
