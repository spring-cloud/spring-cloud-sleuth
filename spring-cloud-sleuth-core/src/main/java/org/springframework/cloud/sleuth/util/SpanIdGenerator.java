package org.springframework.cloud.sleuth.util;

/**
 * Contract for generating id for spans and traces
 *
 * @author Marcin Grzejszczak
 */
public interface SpanIdGenerator {

	long generateId();
}
