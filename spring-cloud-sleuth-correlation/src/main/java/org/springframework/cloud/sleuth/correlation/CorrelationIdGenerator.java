package org.springframework.cloud.sleuth.correlation;

/**
 * @author Spencer Gibb
 */
public interface CorrelationIdGenerator {
	String create();
}
