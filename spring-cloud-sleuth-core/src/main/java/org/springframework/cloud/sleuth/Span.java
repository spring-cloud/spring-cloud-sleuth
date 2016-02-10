/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.Assert;

import lombok.Getter;

/**
 * Class for gathering and reporting statistics about a block of execution.
 * <p/>
 * Spans should form a directed acyclic graph structure. It should be possible to keep
 * following the parents of a span until you arrive at a span with no parents.
 * <p/>
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 */
/*
 * OpenTracing spans can affect the trace tree by creating children. In this way, they are
 * like scoped tracers. Sleuth spans are DTOs, whose sole responsibility is the current
 * span in the trace tree.
 */
@Getter
public class Span {

	public static final String NOT_SAMPLED_NAME = "X-Not-Sampled";
	public static final String PROCESS_ID_NAME = "X-Process-Id";
	public static final String PARENT_ID_NAME = "X-Parent-Id";
	public static final String TRACE_ID_NAME = "X-Trace-Id";
	public static final String SPAN_NAME_NAME = "X-Span-Name";
	public static final String SPAN_ID_NAME = "X-Span-Id";
	public static final List<String> HEADERS = Arrays.asList(SPAN_ID_NAME, TRACE_ID_NAME,
			SPAN_NAME_NAME, PARENT_ID_NAME, PROCESS_ID_NAME, NOT_SAMPLED_NAME);
	public static final String SPAN_EXPORT_NAME = "X-Span-Export";

	private final long begin;
	private long end = 0;
	private final SpanName name;
	private final long traceId;
	private List<Long> parents = new ArrayList<>();
	private final long spanId;
	private boolean remote = false;
	private boolean exportable = true;
	private final Map<String, String> tags = new LinkedHashMap<>();
	private final String processId;
	private final List<Log> logs = new ArrayList<>();
	private final Span savedSpan;

	public Span(Span current, Span savedSpan) {
		this.begin = current.getBegin();
		this.end = current.getEnd();
		this.name = current.getName();
		this.traceId = current.getTraceId();
		this.parents = current.getParents();
		this.spanId = current.getSpanId();
		this.remote = current.isRemote();
		this.exportable = current.isExportable();
		this.processId = current.getProcessId();
		this.tags.putAll(current.tags());
		this.logs.addAll(current.logs());
		this.savedSpan = savedSpan;
	}

	public Span(long begin, long end, SpanName name, long traceId, List<Long> parents,
			long spanId, boolean remote, boolean exportable, String processId) {
		this(begin, end, name, traceId, parents, spanId, remote, exportable, processId,
				null);
	}

	public Span(long begin, long end, SpanName name, long traceId, List<Long> parents,
			long spanId, boolean remote, boolean exportable, String processId,
			Span savedSpan) {
		this.begin = begin <= 0 ? System.currentTimeMillis() : begin;
		this.end = end;
		this.name = name != null ? name : SpanName.NO_NAME;
		this.traceId = traceId;
		this.parents = parents;
		this.spanId = spanId;
		this.remote = remote;
		this.exportable = exportable;
		this.processId = processId;
		this.savedSpan = savedSpan;
	}

	public static SpanBuilder builder() {
		return new SpanBuilder();
	}

	/**
	 * The block has completed, stop the clock
	 */
	public synchronized void stop() {
		if (this.end == 0) {
			if (this.begin == 0) {
				throw new IllegalStateException(
						"Span for " + this.name + " has not been started");
			}
			this.end = System.currentTimeMillis();
		}
	}

	/**
	 * Return the total amount of time elapsed since start was called, if running, or
	 * difference between stop and start
	 */
	public synchronized long getAccumulatedMillis() {
		if (this.begin == 0) {
			return 0;
		}
		if (this.end > 0) {
			return this.end - this.begin;
		}
		return System.currentTimeMillis() - this.begin;
	}

	/**
	 * Has the span been started and not yet stopped?
	 */
	public synchronized boolean isRunning() {
		return this.begin != 0 && this.end == 0;
	}

	/**
	 * Add a tag or data annotation associated with this span
	 */
	public void tag(String key, String value) {
		this.tags.put(key, value);
	}

	/**
	 * Add an {@link Log#event event} to the timeline associated with this span.
	 */
	public void logEvent(String event) {
		this.logs.add(new Log(System.currentTimeMillis(), event));
	}

	/**
	 * Get tag data associated with this span (read only)
	 * <p/>
	 * <p/>
	 * Will never be null.
	 */
	public Map<String, String> tags() {
		return Collections.unmodifiableMap(this.tags);
	}

	/**
	 * Get any timestamped events (read only)
	 * <p/>
	 * <p/>
	 * Will never be null.
	 */
	public List<Log> logs() {
		return Collections.unmodifiableList(this.logs);
	}

	/**
	 * Returns the saved span. The one that was "current" before this Span.
	 * <p>
	 * Might be null
	 */
	public Span getSavedSpan() {
		return this.savedSpan;
	}

	public boolean hasSavedSpan() {
		return this.savedSpan != null;
	}

	/**
	 * A human-readable name assigned to this span instance.
	 * <p>
	 */
	public SpanName getName() {
		return this.name;
	}

	/**
	 * A pseudo-unique (random) number assigned to this span instance.
	 * <p>
	 * <p>
	 * The spanId is immutable and cannot be changed. It is safe to access this from
	 * multiple threads.
	 */
	public long getSpanId() {
		return this.spanId;
	}

	/**
	 * A pseudo-unique (random) number assigned to the trace associated with this span
	 */
	public long getTraceId() {
		return this.traceId;
	}

	/**
	 * Return a unique id for the process from which this Span originated.
	 * <p>
	 * <p>
	 * // TODO: Check when this is going to be null (cause it may be null)
	 */
	public String getProcessId() {
		return this.processId;
	}

	/**
	 * Returns the parent IDs of the span.
	 * <p>
	 * <p>
	 * The collection will be empty if there are no parents.
	 */
	public List<Long> getParents() {
		return this.parents;
	}

	/**
	 * Flag that tells us whether the span was started in another process. Useful in RPC
	 * tracing when the receiver actually has to add annotations to the senders span.
	 */
	public boolean isRemote() {
		return this.remote;
	}

	/**
	 * Get the start time, in milliseconds
	 */
	public long getBegin() {
		return this.begin;
	}

	/**
	 * Get the stop time, in milliseconds
	 */
	public long getEnd() {
		return this.end;
	}

	/**
	 * Is the span eligible for export? If not then we may not need accumulate annotations
	 * (for instance).
	 */
	public boolean isExportable() {
		return this.exportable;
	}

	/**
	 * Represents given long id as hex string
	 */
	public static String toHex(long id) {
		return Long.toHexString(id);
	}

	/**
	 * Represents hex string as long
	 */
	public static long fromHex(String hexString) {
		Assert.hasText(hexString, "Can't convert empty hex string to long");
		return new BigInteger(hexString, 16).longValue();
	}

	@Override
	public String toString() {
		return "[Trace: " + toHex(this.traceId) + ", Span: " + toHex(this.spanId) + ", exportable=" + this.exportable + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (this.spanId ^ (this.spanId >>> 32));
		result = prime * result + (int) (this.traceId ^ (this.traceId >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Span other = (Span) obj;
		if (this.spanId != other.spanId)
			return false;
		if (this.traceId != other.traceId)
			return false;
		return true;
	}

	public static class SpanBuilder {
		private long begin;
		private long end;
		private SpanName name;
		private long traceId;
		private ArrayList<Long> parents = new ArrayList<>();
		private long spanId;
		private boolean remote;
		private boolean exportable = true;
		private String processId;
		private Span savedSpan;
		private List<Log> logs = new ArrayList<>();
		private Map<String, String> tags = new LinkedHashMap<>();

		SpanBuilder() {
		}

		public Span.SpanBuilder begin(long begin) {
			this.begin = begin;
			return this;
		}

		public Span.SpanBuilder end(long end) {
			this.end = end;
			return this;
		}

		public Span.SpanBuilder name(SpanName name) {
			this.name = name;
			return this;
		}

		public Span.SpanBuilder traceId(long traceId) {
			this.traceId = traceId;
			return this;
		}

		public Span.SpanBuilder parent(Long parent) {
			this.parents.add(parent);
			return this;
		}

		public Span.SpanBuilder parents(Collection<Long> parents) {
			this.parents.addAll(parents);
			return this;
		}

		public Span.SpanBuilder log(Log log) {
			this.logs.add(log);
			return this;
		}

		public Span.SpanBuilder logs(Collection<Log> logs) {
			this.logs.addAll(logs);
			return this;
		}

		public Span.SpanBuilder tag(String tagKey, String tagValue) {
			this.tags.put(tagKey, tagValue);
			return this;
		}

		public Span.SpanBuilder tags(Map<String, String> tags) {
			this.tags.putAll(tags);
			return this;
		}

		public Span.SpanBuilder spanId(long spanId) {
			this.spanId = spanId;
			return this;
		}

		public Span.SpanBuilder remote(boolean remote) {
			this.remote = remote;
			return this;
		}

		public Span.SpanBuilder exportable(boolean exportable) {
			this.exportable = exportable;
			return this;
		}

		public Span.SpanBuilder processId(String processId) {
			this.processId = processId;
			return this;
		}

		public Span.SpanBuilder savedSpan(Span savedSpan) {
			this.savedSpan = savedSpan;
			return this;
		}

		public Span build() {
			Span span = new Span(this.begin, this.end, this.name, this.traceId,
					this.parents, this.spanId, this.remote, this.exportable,
					this.processId, this.savedSpan);
			span.logs.addAll(this.logs);
			span.tags.putAll(this.tags);
			return span;
		}

		public String toString() {
			return "org.springframework.cloud.sleuth.Span.SpanBuilder(begin=" + this.begin
					+ ", end=" + this.end + ", name=" + this.name + ", traceId="
					+ this.traceId + ", parents=" + this.parents + ", spanId="
					+ this.spanId + ", remote=" + this.remote + ", exportable="
					+ this.exportable + ", processId=" + this.processId + ", logs="
					+ this.logs + ", tags=" + this.tags + ", savedSpan=" + this.savedSpan
					+ ")";
		}
	}
}
