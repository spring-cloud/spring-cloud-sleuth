package org.springframework.cloud.sleuth.trace;

/**
 * @author Spencer Gibb
 */
public interface IdGenerator {
	String create();
}
