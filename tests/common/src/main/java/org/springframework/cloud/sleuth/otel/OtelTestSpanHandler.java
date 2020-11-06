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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.jetbrains.annotations.NotNull;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.otel.bridge.OtelFinishedSpan;
import org.springframework.cloud.sleuth.otel.exporter.ArrayListSpanProcessor;
import org.springframework.cloud.sleuth.test.TestSpanHandler;

public class OtelTestSpanHandler implements TestSpanHandler, SpanProcessor, SpanExporter {

	private final ArrayListSpanProcessor spanProcessor;

	public OtelTestSpanHandler(ArrayListSpanProcessor spanProcessor) {
		this.spanProcessor = spanProcessor;
	}

	@Override
	public List<FinishedSpan> reportedSpans() {
		return spanProcessor.spans().stream().map(OtelFinishedSpan::new).collect(Collectors.toList());
	}

	@Override
	public FinishedSpan takeLocalSpan() {
		return new OtelFinishedSpan(spanProcessor.takeLocalSpan());
	}

	@Override
	public void clear() {
		spanProcessor.clear();
	}

	@Override
	public FinishedSpan takeRemoteSpan(Span.Kind kind) {
		return reportedSpans().stream().filter(s -> s.getKind().name().equals(kind.name())).findFirst()
				.orElseThrow(() -> new AssertionError("No span with kind [" + kind.name() + "] found."));
	}

	@Override
	public FinishedSpan takeRemoteSpanWithError(Span.Kind kind) {
		return reportedSpans().stream().filter(s -> s.getKind().name().equals(kind.name()) && s.getError() != null)
				.findFirst()
				.orElseThrow(() -> new AssertionError("No span with kind [" + kind.name() + "] and error found."));
	}

	@Override
	public FinishedSpan get(int index) {
		return reportedSpans().get(index);
	}

	@NotNull
	@Override
	public Iterator<FinishedSpan> iterator() {
		return reportedSpans().iterator();
	}

	@Override
	public void onStart(Context parentContext, ReadWriteSpan span) {
		spanProcessor.onStart(parentContext, span);
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
