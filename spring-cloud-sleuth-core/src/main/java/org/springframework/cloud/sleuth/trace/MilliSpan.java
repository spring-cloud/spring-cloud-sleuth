package org.springframework.cloud.sleuth.trace;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;
import java.util.Map;

/**
 * @author Spencer Gibb
 */
@Data
@Builder
public class MilliSpan implements Span {
	private final long begin;
	private long end;
	private final String description;
	private final String traceId;
	@Singular
	private final List<String> parents;
	private final String spanId;
	private final Map<String, String> kVAnnotations;
	private final String processId;
	@Singular
	private final List<TimelineAnnotation> timelineAnnotations;

	@Override
	public synchronized void stop() {
		if (end == 0) {
			if (begin == 0)
				throw new IllegalStateException("Span for " + description
						+ " has not been started");
			end = System.currentTimeMillis();
			//TODO figure out how to Trace.deliver(this)
		}
	}

	@Override
	public synchronized long getAccumulatedMillis() {
		if (begin == 0)
			return 0;
		if (end > 0)
			return end - begin;
		return System.currentTimeMillis() - begin;
	}

	@Override
	public synchronized boolean isRunning() {
		return begin != 0 && end == 0;
	}

	@Override
	public Span child(String description) {
		return null;
	}

	@Override
	public void addKVAnnotation(String key, String value) {
		kVAnnotations.put(key, value);
	}

	@Override
	public void addTimelineAnnotation(String msg) {
		timelineAnnotations.add(new TimelineAnnotation(System.currentTimeMillis(), msg));
	}

}
