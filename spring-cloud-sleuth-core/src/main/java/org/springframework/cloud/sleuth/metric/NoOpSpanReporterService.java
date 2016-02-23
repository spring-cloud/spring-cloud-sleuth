package org.springframework.cloud.sleuth.metric;

/**
 * {@link SpanReporterService} that does nothing
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
public class NoOpSpanReporterService implements SpanReporterService {

	public void incrementAcceptedSpans(long quantity) {

	}

	public void incrementDroppedSpans(long quantity) {

	}
}