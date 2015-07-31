package org.springframework.cloud.sleuth.zipkin;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.zipkin")
@Data
public class ZipkinProperties {
	// Sample rate = 1 means every request will get traced.
	private int fixedSampleRate = 1;
	private String host = "localhost";
	private int port = 9410;
}
