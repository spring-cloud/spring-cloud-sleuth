package org.springframework.cloud.sleuth.zipkin;

import org.springframework.cloud.sleuth.metric.SpanMetricReporter;

import zipkin.reporter.ReporterMetrics;

final class ReporterMetricsAdapter implements ReporterMetrics {
	private final SpanMetricReporter spanMetricReporter;

	public ReporterMetricsAdapter(SpanMetricReporter spanMetricReporter) {
		this.spanMetricReporter = spanMetricReporter;
	}

	@Override
	public ReporterMetrics forTransport(String transport) {
		return this;
	}

	@Override
	public void incrementMessages() {
	}

	@Override
	public void incrementMessagesDropped() {
	}

	@Override
	public void incrementSpans(int i) {
		this.spanMetricReporter.incrementAcceptedSpans(i);
	}

	@Override
	public void incrementSpanBytes(int i) {
	}

	@Override
	public void incrementMessageBytes(int i) {
	}

	@Override public void incrementSpansDropped(int i) {
		this.spanMetricReporter.incrementDroppedSpans(i);
	}
}
