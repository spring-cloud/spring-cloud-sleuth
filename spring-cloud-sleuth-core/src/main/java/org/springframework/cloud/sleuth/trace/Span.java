package org.springframework.cloud.sleuth.trace;

import java.util.List;
import java.util.Map;

/**
 * Base interface for gathering and reporting statistics about a block of
 * execution.
 * <p/>
 * Spans should form a directed acyclic graph structure.  It should be possible
 * to keep following the parents of a span until you arrive at a span with no
 * parents.<p/>
 */
public interface Span {
	/**
	 * The block has completed, stop the clock
	 */
	void stop();

	/**
	 * Get the start time, in milliseconds
	 */
	long getBegin();

	/**
	 * Get the stop time, in milliseconds
	 */
	long getEnd();

	/**
	 * Return the total amount of time elapsed since start was called, if running,
	 * or difference between stop and start
	 */
	long getAccumulatedMillis();

	/**
	 * Has the span been started and not yet stopped?
	 */
	boolean isRunning();

	/**
	 * Return a textual description of this span.<p/>
	 * <p/>
	 * Will never be null.
	 */
	String getDescription();

	/**
	 * A pseudo-unique (random) number assigned to this span instance.<p/>
	 * <p/>
	 * The spanId is immutable and cannot be changed.  It is safe to access this
	 * from multiple threads.
	 */
	String getSpanId();

	/**
	 * A pseudo-unique (random) number assigned to the trace associated with this
	 * span
	 */
	String getTraceId();

	/**
	 * Returns the parent IDs of the span.<p/>
	 * <p/>
	 * The collection will be empty if there are no parents.
	 */
	List<String> getParents();

	/**
	 * Add a data annotation associated with this span
	 */
	void addKVAnnotation(String key, String value);

	/**
	 * Add a timeline annotation associated with this span
	 */
	void addTimelineAnnotation(String msg);

	/**
	 * Get data associated with this span (read only)<p/>
	 * <p/>
	 * Will never be null.
	 */
	Map<String, String> getKVAnnotations();

	/**
	 * Get any timeline annotations (read only)<p/>
	 * <p/>
	 * Will never be null.
	 */
	List<TimelineAnnotation> getTimelineAnnotations();

	/**
	 * Return a unique id for the process from which this Span originated.<p/>
	 * <p/>
	 * Will never be null.
	 */
	String getProcessId();
}
