package org.springframework.cloud.sleuth.metric;

/**
 * Span reporting service that does nothing
 *
 * @author Marcin Grzejszczak
 */
public class NoOpSpanReporterService implements SpanReporterService {

	public void incrementAcceptedSpans(long quantity) {

	}

	public void incrementDroppedSpans(long quantity) {

	}
}