/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.otel;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.opentelemetry.common.AttributeConsumer;
import io.opentelemetry.common.AttributeKey;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.jetbrains.annotations.NotNull;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.test.ReportedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;

public class OtelTestSpanHandler implements TestSpanHandler, SpanProcessor, SpanExporter {

	private final ArrayListSpanProcessor spanProcessor;

	public OtelTestSpanHandler(ArrayListSpanProcessor spanProcessor) {
		this.spanProcessor = spanProcessor;
	}

	@Override
	public List<ReportedSpan> reportedSpans() {
		return spanProcessor.spans.stream().map(SpanDataToReportedSpan::new).collect(Collectors.toList());
	}

	@Override
	public ReportedSpan takeLocalSpan() {
		return new SpanDataToReportedSpan(spanProcessor.takeLocalSpan());
	}

	@Override
	public void clear() {
		spanProcessor.clear();
	}

	@Override
	public ReportedSpan takeRemoteSpan(Span.Kind kind) {
		return reportedSpans().stream().filter(s -> s.kind().name().equals(kind.name())).findFirst()
				.orElseThrow(() -> new AssertionError("No span with kind [" + kind.name() + "] found."));
	}

	@Override
	public ReportedSpan takeRemoteSpanWithError(Span.Kind kind) {
		// TODO: [OTEL] What does it mean?
		return null;
	}

	@Override
	public ReportedSpan get(int index) {
		return reportedSpans().get(index);
	}

	@NotNull
	@Override
	public Iterator<ReportedSpan> iterator() {
		return reportedSpans().iterator();
	}

	@Override
	public void onStart(ReadWriteSpan span) {
		spanProcessor.onStart(span);
	}

	@Override
	public boolean isStartRequired() {
		return spanProcessor.isStartRequired();
	}

	@Override
	public void onEnd(ReadableSpan span) {
		spanProcessor.onEnd(span);
	}

	@Override
	public boolean isEndRequired() {
		return spanProcessor.isEndRequired();
	}

	@Override
	public CompletableResultCode export(Collection<SpanData> spans) {
		return spanProcessor.export(spans);
	}

	@Override
	public CompletableResultCode flush() {
		return spanProcessor.flush();
	}

	@Override
	public CompletableResultCode shutdown() {
		return spanProcessor.shutdown();
	}

	@Override
	public CompletableResultCode forceFlush() {
		return spanProcessor.forceFlush();
	}

}

class SpanDataToReportedSpan implements ReportedSpan {

	private final SpanData spanData;

	private final Map<String, String> tags = new HashMap<>();

	SpanDataToReportedSpan(SpanData spanData) {
		this.spanData = spanData;
	}

	@Override
	public String name() {
		return this.spanData.getName();
	}

	@Override
	public long finishTimestamp() {
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
	public Collection<Map.Entry<Long, String>> annotations() {
		return this.spanData.getEvents().stream()
				.map(e -> new AbstractMap.SimpleEntry<>(e.getEpochNanos(), e.getName())).collect(Collectors.toList());
	}

	@Override
	public String id() {
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
	public String toString() {
		return "SpanDataToReportedSpan{" + "spanData=" + spanData + ", tags=" + tags + '}';
	}

}