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

package org.springframework.cloud.sleuth.otel.bridge;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Stores spans in a queue.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class ArrayListSpanProcessor implements SpanProcessor, SpanExporter {

	Queue<SpanData> spans = new LinkedBlockingQueue<>(50);

	@Override
	public void onStart(Context parent, ReadWriteSpan span) {

	}

	@Override
	public boolean isStartRequired() {
		return false;
	}

	@Override
	public void onEnd(ReadableSpan span) {
		this.spans.add(span.toSpanData());
	}

	@Override
	public boolean isEndRequired() {
		return true;
	}

	@Override
	public CompletableResultCode export(Collection<SpanData> spans) {
		return CompletableResultCode.ofSuccess();
	}

	@Override
	public CompletableResultCode flush() {
		return CompletableResultCode.ofSuccess();
	}

	@Override
	public CompletableResultCode shutdown() {
		return CompletableResultCode.ofSuccess();
	}

	@Override
	public CompletableResultCode forceFlush() {
		return CompletableResultCode.ofSuccess();
	}

	public SpanData takeLocalSpan() {
		return this.spans.poll();
	}

	public Queue<SpanData> spans() {
		return this.spans;
	}

	public void clear() {
		this.spans.clear();
	}

	@Override
	public String toString() {
		return "ArrayListSpanProcessor{" + "spans=" + spans + '}';
	}

}
