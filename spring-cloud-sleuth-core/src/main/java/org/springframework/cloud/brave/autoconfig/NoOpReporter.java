package org.springframework.cloud.brave.autoconfig;

import zipkin2.reporter.Reporter;

/**
 * {@link Reporter} that does nothing
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public class NoOpReporter implements Reporter<zipkin2.Span> {
	@Override public void report(zipkin2.Span o) {

	}
}
