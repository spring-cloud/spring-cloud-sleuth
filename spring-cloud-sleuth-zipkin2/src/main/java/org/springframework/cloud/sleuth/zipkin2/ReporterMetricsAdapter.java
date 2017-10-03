package org.springframework.cloud.sleuth.zipkin2;

import org.springframework.cloud.sleuth.metric.SpanMetricReporter;

import zipkin2.reporter.ReporterMetrics;

final class ReporterMetricsAdapter implements ReporterMetrics {
	private final SpanMetricReporter spanMetricReporter;

	public ReporterMetricsAdapter(SpanMetricReporter spanMetricReporter) {
		this.spanMetricReporter = spanMetricReporter;
	}

	@Override
	public void incrementMessages() {
	}

	@Override
	public void incrementMessagesDropped(Throwable throwable) {
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

	@Override
	public void incrementSpansDropped(int i) {
		this.spanMetricReporter.incrementDroppedSpans(i);
	}

	@Override
	public void updateQueuedSpans(int i) {
	}

	@Override
	public void updateQueuedBytes(int i) {
	}
}
