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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Class for gathering and reporting statistics about a block of execution.
 * <p>
 * Spans should form a directed acyclic graph structure. It should be possible to keep
 * following the parents of a span until you arrive at a span with no parents.
 * <p>
 * Spans can be either annotated with tags or logs.
 * <p>
 * An <b>Annotation</b> is used to record existence of an event in time. Below you can
 * find some of the core annotations used to define the start and stop of a request:
 * <p>
 * <ul>
 * <li><b>cs</b> - Client Sent</li>
 * <li><b>sr</b> - Server Received</li>
 * <li><b>ss</b> - Server Sent</li>
 * <li><b>cr</b> - Client Received</li>
 * </ul>
 *
 * Spring Cloud Sleuth uses Zipkin compatible header names
 *
 * <ul>
 * <li>X-B3-TraceId: 64 encoded bits</li>
 * <li>X-B3-SpanId: 64 encoded bits</li>
 * <li>X-B3-ParentSpanId: 64 encoded bits</li>
 * <li>X-B3-Sampled: Boolean (either “1” or “0”)</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
/*
 * OpenTracing spans can affect the trace tree by creating children. In this way, they are
 * like scoped tracers. Sleuth spans are DTOs, whose sole responsibility is the current
 * span in the trace tree.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class Span {

	public static final String SAMPLED_NAME = "X-B3-Sampled";
	public static final String PROCESS_ID_NAME = "X-Process-Id";
	public static final String PARENT_ID_NAME = "X-B3-ParentSpanId";
	public static final String TRACE_ID_NAME = "X-B3-TraceId";
	public static final String SPAN_NAME_NAME = "X-Span-Name";
	public static final String SPAN_ID_NAME = "X-B3-SpanId";
	public static final String SPAN_EXPORT_NAME = "X-Span-Export";
	public static final Set<String> SPAN_HEADERS = new HashSet<>(
			Arrays.asList(SAMPLED_NAME, PROCESS_ID_NAME, PARENT_ID_NAME, TRACE_ID_NAME,
					SPAN_ID_NAME, SPAN_NAME_NAME, SPAN_EXPORT_NAME));

	public static final String SPAN_SAMPLED = "1";
	public static final String SPAN_NOT_SAMPLED = "0";

	public static final String SPAN_LOCAL_COMPONENT_TAG_NAME = "lc";

	/**
	 * <b>cr</b> - Client Receive. Signifies the end of the span. The client has
	 * successfully received the response from the server side. If one subtracts the cs
	 * timestamp from this timestamp one will receive the whole time needed by the client
	 * to receive the response from the server.
	 */
	public static final String CLIENT_RECV = "cr";

	/**
	 * <b>cs</b> - Client Sent. The client has made a request (a client can be e.g.
	 * {@link org.springframework.web.client.RestTemplate}. This annotation depicts the
	 * start of the span.
	 */
	// For an outbound RPC call, it should log a "cs" annotation.
	// If possible, it should log a binary annotation of "sa", indicating the
	// destination address.
	public static final String CLIENT_SEND = "cs";

	/**
	 * <b>sr</b> - Server Receive. The server side got the request and will start
	 * processing it. If one subtracts the cs timestamp from this timestamp one will
	 * receive the network latency.
	 */
	// If an inbound RPC call, it should log a "sr" annotation.
	// If possible, it should log a binary annotation of "ca", indicating the
	// caller's address (ex X-Forwarded-For header)
	public static final String SERVER_RECV = "sr";

	/**
	 * <b>ss</b> - Server Send. Annotated upon completion of request processing (when the
	 * response got sent back to the client). If one subtracts the sr timestamp from this
	 * timestamp one will receive the time needed by the server side to process the
	 * request.
	 */
	public static final String SERVER_SEND = "ss";

	/**
	 * <a href="https://github.com/opentracing/opentracing-go/blob/master/ext/tags.go">As
	 * in Open Tracing</a>
	 */
	public static final String SPAN_PEER_SERVICE_TAG_NAME = "peer.service";

	private final long begin;
	private long end = 0;
	private final String name;
	private final long traceId;
	private List<Long> parents = new ArrayList<>();
	private final long spanId;
	private boolean remote = false;
	private boolean exportable = true;
	private final Map<String, String> tags;
	private final String processId;
	private final List<Log> logs;
	private final Span savedSpan;

	@SuppressWarnings("unused")
	private Span() {
		this(-1, -1, "dummy", 0, Collections.<Long>emptyList(), 0, false, false, null);
	}

	/**
	 * Creates a new span that still tracks tags and logs of the current span. This is
	 * crucial when continuing spans since the changes in those collections done in the
	 * continued span need to be reflected until the span gets closed.
	 */
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
		this.tags = current.tags;
		this.logs = current.logs;
		this.savedSpan = savedSpan;
	}

	public Span(long begin, long end, String name, long traceId, List<Long> parents,
			long spanId, boolean remote, boolean exportable, String processId) {
		this(begin, end, name, traceId, parents, spanId, remote, exportable, processId,
				null);
	}

	public Span(long begin, long end, String name, long traceId, List<Long> parents,
			long spanId, boolean remote, boolean exportable, String processId,
			Span savedSpan) {
		this.begin = begin <= 0 ? System.currentTimeMillis() : begin;
		this.end = end;
		this.name = name != null ? name : "";
		this.traceId = traceId;
		this.parents = parents;
		this.spanId = spanId;
		this.remote = remote;
		this.exportable = exportable;
		this.processId = processId;
		this.savedSpan = savedSpan;
		this.tags = new LinkedHashMap<>();
		this.logs = new ArrayList<>();
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
	@JsonIgnore
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
	@JsonIgnore
	public synchronized boolean isRunning() {
		return this.begin != 0 && this.end == 0;
	}

	/**
	 * Add a tag or data annotation associated with this span. The tag will be added only
	 * if it has a value.
	 */
	public void tag(String key, String value) {
		if (StringUtils.hasText(value)) {
			this.tags.put(key, value);
		}
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
	 * Returns the saved span. The one that was "current" before this span.
	 * <p>
	 * Might be null
	 */
	@JsonIgnore
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
	public String getName() {
		return this.name;
	}

	/**
	 * A pseudo-unique (random) number assigned to this span instance.
	 * <p>
	 * <p>
	 * The span id is immutable and cannot be changed. It is safe to access this from
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
	 * Return a unique id for the process from which this span originated.
	 * <p>
	 * Might be null
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
	public static String idToHex(long id) {
		return Long.toHexString(id);
	}

	/**
	 * Represents hex string as long
	 */
	public static long hexToId(String hexString) {
		Assert.hasText(hexString, "Can't convert empty hex string to long");
		return new BigInteger(hexString, 16).longValue();
	}

	@Override
	public String toString() {
		return "[Trace: " + idToHex(this.traceId) + ", Span: " + idToHex(this.spanId)
				+ ", Parent: " + getParentIdIfPresent() + ", exportable:" + this.exportable + "]";
	}

	private String getParentIdIfPresent() {
		return this.getParents().isEmpty() ? "null" : idToHex(this.getParents().get(0));
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
		private String name;
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

		public Span.SpanBuilder name(String name) {
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

		@Override
		public String toString() {
			return "SpanBuilder{" + "begin=" + this.begin + ", end=" + this.end
					+ ", name=" + this.name + ", traceId=" + this.traceId + ", parents="
					+ this.parents + ", spanId=" + this.spanId + ", remote=" + this.remote
					+ ", exportable=" + this.exportable + ", processId='" + this.processId
					+ '\'' + ", savedSpan=" + this.savedSpan + ", logs=" + this.logs
					+ ", tags=" + this.tags + '}';
		}
	}
}
