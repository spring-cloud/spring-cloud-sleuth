/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.actuate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.exporter.SpanReporter;

/**
 * A {@link SpanReporter} that buffers finished spans.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class BufferingSpanReporter implements SpanReporter {

	private final int capacity;

	private final AtomicInteger estimatedSize = new AtomicInteger();

	final ConcurrentLinkedQueue<FinishedSpan> spans = new ConcurrentLinkedQueue<>();

	public BufferingSpanReporter(int capacity) {
		this.capacity = capacity;
	}

	/**
	 * Return a snapshot of currently buffered spans.
	 * <p>
	 * This will not remove spans from the buffer, see {@link #drainFinishedSpans()} ()}
	 * for its counterpart.
	 * @return a snapshot of currently buffered spans.
	 */
	public List<FinishedSpan> getFinishedSpans() {
		return new ArrayList<>(this.spans);
	}

	/**
	 * Return the {@link StartupTimeline timeline} by pulling spans from the buffer.
	 * <p>
	 * This removes steps from the buffer, see {@link #getFinishedSpans()} for its
	 * read-only counterpart.
	 * @return buffered steps drained from the buffer.
	 */
	public List<FinishedSpan> drainFinishedSpans() {
		List<FinishedSpan> events = new ArrayList<>();
		Iterator<FinishedSpan> iterator = this.spans.iterator();
		while (iterator.hasNext()) {
			events.add(iterator.next());
			iterator.remove();
		}
		this.estimatedSize.set(0);
		return events;
	}

	@Override
	public void report(FinishedSpan span) {
		if (this.estimatedSize.get() < this.capacity) {
			this.estimatedSize.incrementAndGet();
			this.spans.add(span);
		}
	}
}
