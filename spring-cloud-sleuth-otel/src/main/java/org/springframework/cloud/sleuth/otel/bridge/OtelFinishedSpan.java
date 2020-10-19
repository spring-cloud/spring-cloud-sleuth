package org.springframework.cloud.sleuth.otel.bridge;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.opentelemetry.common.AttributeConsumer;
import io.opentelemetry.common.AttributeKey;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;

public class OtelFinishedSpan implements FinishedSpan {

	private final SpanData spanData;

	private final Map<String, String> tags = new HashMap<>();

	public OtelFinishedSpan(SpanData spanData) {
		this.spanData = spanData;
	}

	@Override
	public String name() {
		return this.spanData.getName();
	}

	@Override
	public long startTimestamp() {
		return this.spanData.getStartEpochNanos();
	}

	@Override
	public long endTimestamp() {
		return this.spanData.getEndEpochNanos();
	}

	@Override
	public Map<String, String> tags() {
		if (this.tags.isEmpty()) {
			this.spanData.getAttributes().forEach(new AttributeConsumer() {
				@Override
				public <T> void consume(AttributeKey<T> key, T value) {
					tags.put(key.getKey(), String.valueOf(value));
				}
			});
		}
		return this.tags;
	}

	@Override
	public Collection<Map.Entry<Long, String>> events() {
		return this.spanData.getEvents().stream()
				.map(e -> new AbstractMap.SimpleEntry<>(e.getEpochNanos(), e.getName())).collect(Collectors.toList());
	}

	@Override
	public String spanId() {
		return this.spanData.getSpanId();
	}

	@Override
	public String parentId() {
		return this.spanData.getParentSpanId();
	}

	@Override
	public String remoteIp() {
		return tags().get("net.peer.name");
	}

	@Override
	public int remotePort() {
		return Integer.valueOf(tags().get("net.peer.port"));
	}

	@Override
	public String traceId() {
		return this.spanData.getTraceId();
	}

	@Override
	public Throwable error() {
		Attributes attributes = this.spanData.getEvents().stream().filter(e -> e.getName().equals("exception"))
				.findFirst().map(e -> e.getAttributes()).orElse(null);
		if (attributes != null) {
			return new AssertingThrowable(attributes);
		}
		return null;
	}

	@Override
	public Span.Kind kind() {
		if (this.spanData.getKind() == io.opentelemetry.trace.Span.Kind.INTERNAL) {
			return null;
		}
		return Span.Kind.valueOf(this.spanData.getKind().name());
	}

	@Override
	public String remoteServiceName() {
		return this.spanData.getAttributes().get(AttributeKey.stringKey("peer.service"));
	}

	@Override
	public String toString() {
		return "SpanDataToReportedSpan{" + "spanData=" + spanData + ", tags=" + tags + '}';
	}

	public static FinishedSpan fromOtel(SpanData span) {
		return new OtelFinishedSpan(span);
	}

	public static class AssertingThrowable extends Throwable {

		public final Attributes attributes;

		AssertingThrowable(Attributes attributes) {
			super(attributes.get(AttributeKey.stringKey("exception.message")));
			this.attributes = attributes;
		}

	}

}
