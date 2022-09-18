/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

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
public class Span implements SpanContext {

	public static final String B3_NAME = "b3";
	public static final String SAMPLED_NAME = "X-B3-Sampled";
	public static final String PROCESS_ID_NAME = "X-Process-Id";
	public static final String PARENT_ID_NAME = "X-B3-ParentSpanId";
	public static final String TRACE_ID_NAME = "X-B3-TraceId";
	public static final String SPAN_NAME_NAME = "X-Span-Name";
	public static final String SPAN_ID_NAME = "X-B3-SpanId";
	public static final String SPAN_EXPORT_NAME = "X-Span-Export";
	public static final String SPAN_FLAGS = "X-B3-Flags";
	public static final String SPAN_BAGGAGE_HEADER_PREFIX = "baggage";
	public static final Set<String> SPAN_HEADERS = new HashSet<>(
			Arrays.asList(SAMPLED_NAME, PROCESS_ID_NAME, PARENT_ID_NAME, TRACE_ID_NAME,
					SPAN_ID_NAME, SPAN_NAME_NAME, SPAN_EXPORT_NAME));

	public static final String SPAN_SAMPLED = "1";
	public static final String SPAN_NOT_SAMPLED = "0";

	public static final String SPAN_LOCAL_COMPONENT_TAG_NAME = "lc";
	public static final String SPAN_ERROR_TAG_NAME = "error";

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

	/**
	 * ID of the instance from which the span was originated.
	 */
	public static final String INSTANCEID = "spring.instance_id";

	private final long begin;
	private long end = 0;
	private String name;
	private final long traceIdHigh;
	private final long traceId;
	private List<Long> parents = new ArrayList<>();
	private final long spanId;
	private boolean remote = false;
	private boolean exportable = true;
	private final Map<String, String> tags;
	private final String processId;
	private final Collection<Log> logs;
	private final Span savedSpan;
	@JsonIgnore
	private final Map<String,String> baggage;

	// Null means we don't know the start tick, so fallback to time
	@JsonIgnore
	private final Long startNanos;
	private Long durationMicros; // serialized in json so micros precision isn't lost
	/*
	 Using B3 propagation, it is most typical to share the same span ID across client and
	 the server. This has backend implications like who owns the timestamp (hint the
	 client does). When a SpanReporter receives a completed span, it should know if it
	 is shared or not.
	  */
	private final boolean shared;

	@SuppressWarnings("unused")
	private Span() {
		this(-1, -1, "dummy", 0, Collections.<Long>emptyList(), 0, false, false, null);
	}

	/**
	 * Creates a new span that still tracks tags and logs of the current span. This is
	 * crucial when continuing spans since the changes in those collections done in the
	 * continued span need to be reflected until the span gets closed.
	 *
	 * @deprecated - use {@link SpanBuilder}
	 */
	@Deprecated
	public Span(Span current, Span savedSpan) {
		this.begin = current.getBegin();
		this.end = current.getEnd();
		this.name = current.getName();
		this.traceIdHigh = current.getTraceIdHigh();
		this.traceId = current.getTraceId();
		this.parents = current.getParents();
		this.spanId = current.getSpanId();
		this.remote = current.isRemote();
		this.exportable = current.isExportable();
		this.processId = current.getProcessId();
		this.tags = current.tags;
		this.logs = current.logs;
		this.startNanos = current.startNanos;
		this.durationMicros = current.durationMicros;
		this.baggage = current.baggage;
		this.savedSpan = savedSpan;
		this.shared = current.shared;
	}

	Span(long begin, long end, String name, long traceId, List<Long> parents,
			long spanId, boolean remote, boolean exportable, String processId) {
		this(begin, end, name, traceId, parents, spanId, remote, exportable, processId,
				null, false);
	}

	Span(long begin, long end, String name, long traceId, List<Long> parents,
			long spanId, boolean remote, boolean exportable, String processId,
			Span savedSpan, boolean shared) {
		this(new SpanBuilder()
				.begin(begin)
				.end(end)
				.name(name)
				.traceId(traceId)
				.parents(parents)
				.spanId(spanId)
				.remote(remote)
				.exportable(exportable)
				.processId(processId)
				.savedSpan(savedSpan)
				.shared(shared));
	}

	Span(SpanBuilder builder) {
		if (builder.begin > 0) { // conventionally, 0 indicates unset
			this.startNanos = null; // don't know the start tick
			this.begin = builder.begin;
		} else {
			this.startNanos = nanoTime();
			this.begin = System.currentTimeMillis();
		}
		if (builder.end > 0) {
			this.end = builder.end;
			this.durationMicros = (this.end - this.begin) * 1000;
		}
		this.name = builder.name != null ? builder.name : "";
		this.traceIdHigh = builder.traceIdHigh;
		this.traceId = builder.traceId;
		this.parents.addAll(builder.parents);
		this.spanId = builder.spanId;
		this.remote = builder.remote;
		this.exportable = builder.exportable;
		this.processId = builder.processId;
		this.savedSpan = builder.savedSpan;
		this.tags = new ConcurrentHashMap<>();
		this.tags.putAll(builder.tags);
		this.logs = new ConcurrentLinkedQueue<>();
		this.logs.addAll(builder.logs);
		this.baggage = new ConcurrentHashMap<>();
		this.baggage.putAll(builder.baggage);
		this.shared = builder.shared;
	}

	public static SpanBuilder builder() {
		return new SpanBuilder();
	}

	/**
	 * The block has completed, stop the clock
	 */
	public synchronized void stop() {
		if (this.durationMicros == null) {
			if (this.begin == 0) {
				throw new IllegalStateException(
						"Span for " + this.name + " has not been started");
			}
			if (this.end == 0) {
				this.end = System.currentTimeMillis();
			}
			if (this.startNanos != null) { // set a precise duration
				this.durationMicros = Math.max(1, (nanoTime() - this.startNanos) / 1000);
			} else {
				this.durationMicros = (this.end - this.begin) * 1000;
			}
		}
	}

	/**
	 * Return the total amount of time elapsed since start was called, if running, or
	 * difference between stop and start, in microseconds.
	 *
	 * Note that in case of the spans that have CS / CR events we will not
	 * send to Zipkin the accumulated microseconds but will calculate the
	 * duration basing on the timestamps of the CS / CR events.
	 *
	 * @return zero if not running, or a positive number of microseconds.
	 */
	@JsonIgnore
	public synchronized long getAccumulatedMicros() {
		if (this.durationMicros != null) {
			return this.durationMicros;
		} else { // stop() hasn't yet been called
			if (this.begin == 0) {
				return 0;
			}
			if (this.startNanos != null) {
				return Math.max(1, (nanoTime() - this.startNanos) / 1000);
			} else  {
				return (System.currentTimeMillis() - this.begin) * 1000;
			}
		}
	}

	// Visible for testing
	@JsonIgnore
	long nanoTime() {
		return System.nanoTime();
	}

	/**
	 * Has the span been started and not yet stopped?
	 */
	@JsonIgnore
	public synchronized boolean isRunning() {
		return this.begin != 0 && this.durationMicros == null;
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
		logEvent(System.currentTimeMillis(), event);
	}

	/**
	 * Add a {@link Log#event event} to a specific point (a timestamp in milliseconds) in the timeline
	 * associated with this span.
	 */
	public void logEvent(long timestampMilliseconds, String event) {
		this.logs.add(new Log(timestampMilliseconds, event));
	}

	/**
	 * Sets a baggage item in the Span (and its SpanContext) as a key/value pair.
	 *
	 * Baggage enables powerful distributed context propagation functionality where arbitrary application data can be
	 * carried along the full path of request execution throughout the system.
	 *
	 * Note 1: Baggage is only propagated to the future (recursive) children of this SpanContext.
	 *
	 * Note 2: Baggage is sent in-band with every subsequent local and remote calls, so this feature must be used with
	 * care.
	 *
	 * @return this Span instance, for chaining
	 */
	public Span setBaggageItem(String key, String value) {
		this.baggage.put(key.toLowerCase(), value);
		return this;
	}

	/**
	 * @return the value of the baggage item identified by the given key, or null if no such item could be found
	 */
	public String getBaggageItem(String key) {
		return this.baggage.get(key.toLowerCase());
	}

	@Override
	public final Iterable<Map.Entry<String,String>> baggageItems() {
		return this.baggage.entrySet();
	}

	public final Map<String,String> getBaggage() {
		return Collections.unmodifiableMap(this.baggage);
	}

	/**
	 * Get tag data associated with this span (read only)
	 * <p/>
	 * <p/>
	 * Will never be null.
	 */
	public Map<String, String> tags() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(this.tags));
	}

	/**
	 * Get any timestamped events (read only)
	 * <p/>
	 * <p/>
	 * Will never be null.
	 */
	public List<Log> logs() {
		return Collections.unmodifiableList(new ArrayList<>(this.logs));
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

	public void setName(String name) {
		this.name = name;
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
	 * When non-zero, the trace containing this span uses 128-bit trace identifiers.
	 *
	 * <p>{@code traceIdHigh} corresponds to the high bits in big-endian format and
	 * {@link #getTraceId()} corresponds to the low bits.
	 *
	 * <p>Ex. to convert the two fields to a 128bit opaque id array, you'd use code like below.
	 * <pre>{@code
	 * ByteBuffer traceId128 = ByteBuffer.allocate(16);
	 * traceId128.putLong(span.getTraceIdHigh());
	 * traceId128.putLong(span.getTraceId());
	 * traceBytes = traceId128.array();
	 * }</pre>
	 *
	 * @see #traceIdString()
	 * @since 1.0.11
	 */
	public long getTraceIdHigh() {
		return this.traceIdHigh;
	}

	/**
	 * Unique 8-byte identifier for a trace, set on all spans within it.
	 *
	 * @see #getTraceIdHigh() for notes about 128-bit trace identifiers
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
	 * Span and trace id got extracted from a carrier?
	 * We are adding data to the same span created by a remote client
	 *
	 * @since 1.3.0
	 */
	public boolean isShared() {
		return this.shared;
	}

	/**
	 * Returns the 16 or 32 character hex representation of the span's trace ID
	 *
	 * @since 1.0.11
	 */
	public String traceIdString() {
		if (this.traceIdHigh != 0) {
			char[] result = new char[32];
			writeHexLong(result, 0, this.traceIdHigh);
			writeHexLong(result, 16, this.traceId);
			return new String(result);
		}
		char[] result = new char[16];
		writeHexLong(result, 0, this.traceId);
		return new String(result);
	}

	/**
	 * Converts the span to a {@link SpanBuilder} format
	 */
	public SpanBuilder toBuilder() {
		return builder().from(this);
	}

	/**
	 * Represents given long id as 16-character lower-hex string
	 *
	 * @see #traceIdString()
	 */
	public static String idToHex(long id) {
		char[] data = new char[16];
		writeHexLong(data, 0, id);
		return new String(data);
	}

	/** Inspired by {@code okio.Buffer.writeLong} */
	static void writeHexLong(char[] data, int pos, long v) {
		writeHexByte(data, pos + 0,  (byte) ((v >>> 56L) & 0xff));
		writeHexByte(data, pos + 2,  (byte) ((v >>> 48L) & 0xff));
		writeHexByte(data, pos + 4,  (byte) ((v >>> 40L) & 0xff));
		writeHexByte(data, pos + 6,  (byte) ((v >>> 32L) & 0xff));
		writeHexByte(data, pos + 8,  (byte) ((v >>> 24L) & 0xff));
		writeHexByte(data, pos + 10, (byte) ((v >>> 16L) & 0xff));
		writeHexByte(data, pos + 12, (byte) ((v >>> 8L) & 0xff));
		writeHexByte(data, pos + 14, (byte)  (v & 0xff));
	}

	static void writeHexByte(char[] data, int pos, byte b) {
		data[pos + 0] = HEX_DIGITS[(b >> 4) & 0xf];
		data[pos + 1] = HEX_DIGITS[b & 0xf];
	}

	static final char[] HEX_DIGITS =
			{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

	/**
	 * Parses a 1 to 32 character lower-hex string with no prefix into an unsigned long, tossing any
	 * bits higher than 64.
	 */
	public static long hexToId(String hexString) {
		Assert.hasText(hexString, "Can't convert empty hex string to long");
		int length = hexString.length();
		if (length < 1 || length > 32) throw new IllegalArgumentException("Malformed id: " + hexString);

		// trim off any high bits
		int beginIndex = length > 16 ? length - 16 : 0;

		return hexToId(hexString, beginIndex);
	}

	/**
	 * Parses a 16 character lower-hex string with no prefix into an unsigned long, starting at the
	 * specified index.
	 *
	 * @since 1.0.11
	 */
	public static long hexToId(String lowerHex, int index) {
		Assert.hasText(lowerHex, "Can't convert empty hex string to long");
		long result = 0;
		for (int endIndex = Math.min(index + 16, lowerHex.length()); index < endIndex; index++) {
			char c = lowerHex.charAt(index);
			result <<= 4;
			if (c >= '0' && c <= '9') {
				result |= c - '0';
			} else if (c >= 'a' && c <= 'f') {
				result |= c - 'a' + 10;
			} else {
				throw new IllegalArgumentException("Malformed id: " + lowerHex);
			}
		}
		return result;
	}

	@Override
	public String toString() {
		return "[Trace: " + traceIdString() + ", Span: " + idToHex(this.spanId)
				+ ", Parent: " + getParentIdIfPresent() + ", exportable:" + this.exportable + "]";
	}

	private String getParentIdIfPresent() {
		return this.getParents().isEmpty() ? "null" : idToHex(this.getParents().get(0));
	}

	@Override
	public int hashCode() {
		int h = 1;
		h *= 1000003;
		h ^= (this.traceIdHigh >>> 32) ^ this.traceIdHigh;
		h *= 1000003;
		h ^= (this.traceId >>> 32) ^ this.traceId;
		h *= 1000003;
		h ^= (this.spanId >>> 32) ^ this.spanId;
		h *= 1000003;
		return h;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Span) {
			Span that = (Span) o;
			return (this.traceIdHigh == that.traceIdHigh)
					&& (this.traceId == that.traceId)
					&& (this.spanId == that.spanId);
		}
		return false;
	}

	public static class SpanBuilder {
		private long begin;
		private long end;
		private String name;
		private long traceIdHigh;
		private long traceId;
		private final ArrayList<Long> parents = new ArrayList<>();
		private long spanId;
		private boolean remote;
		private boolean exportable = true;
		private String processId;
		private Span savedSpan;
		private final List<Log> logs = new ArrayList<>();
		private final Map<String, String> tags = new LinkedHashMap<>();
		private final Map<String, String> baggage = new LinkedHashMap<>();
		private boolean shared;

		SpanBuilder() {
		}

		/**
		 * Call this to record a begin time of a Span you didn't start. Don't call this when you are
		 * starting the span.
		 *
		 * <p>In other words, don't call {@code builder.begin(System.currentTimeMillis());}. doing so is
		 * redundant and will result in less precision when calculating elapsed time.
		 */
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

		public Span.SpanBuilder traceIdHigh(long traceIdHigh) {
			this.traceIdHigh = traceIdHigh;
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
			this.parents.clear();
			this.parents.addAll(parents);
			return this;
		}

		public Span.SpanBuilder log(Log log) {
			this.logs.add(log);
			return this;
		}

		public Span.SpanBuilder logs(Collection<Log> logs) {
			this.logs.clear();
			this.logs.addAll(logs);
			return this;
		}

		public Span.SpanBuilder tag(String tagKey, String tagValue) {
			this.tags.put(tagKey, tagValue);
			return this;
		}

		public Span.SpanBuilder tags(Map<String, String> tags) {
			this.tags.clear();
			this.tags.putAll(tags);
			return this;
		}

		public Span.SpanBuilder baggage(String baggageKey, String baggageValue) {
			this.baggage.put(baggageKey.toLowerCase(), baggageValue);
			return this;
		}

		public Span.SpanBuilder baggage(Map<String, String> baggage) {
			this.baggage.putAll(baggage);
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

		public Span.SpanBuilder shared(boolean shared) {
			this.shared = shared;
			return this;
		}

		/**
		 * Creates a {@link Span.SpanBuilder} from the {@link Span}.
		 */
		public Span.SpanBuilder from(Span span) {
			return begin(span.begin).end(span.end).name(span.name)
					.traceIdHigh(span.traceIdHigh).traceId(span.traceId)
					.parents(span.getParents()).logs(span.logs).tags(span.tags).baggage(span.baggage)
					.spanId(span.spanId).remote(span.remote).exportable(span.exportable)
					.processId(span.processId).savedSpan(span.savedSpan);
		}

		/**
		 * Builds a span. All collections lik baggage / tags / logs are *copied*, not continued.
		 * In other words if you add a tag to the input {@link Span}, the created span
		 * will not reflect that change.
		 */
		public Span build() {
			return new Span(this);
		}

		@Override
		public String toString() {
			return new Span(this).toString();
		}
	}
}
