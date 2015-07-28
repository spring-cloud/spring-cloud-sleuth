package org.springframework.cloud.sleuth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.NonFinal;

/**
 * @author Spencer Gibb
 */
@Value
@Builder
public class MilliSpan implements Span {
	private long begin;
	@NonFinal
	private long end = 0;
	private String name;
	@NonNull
	private Type type;
	private String traceId;
	@Singular
	private List<String> parents;
	private String spanId;
	private Map<String, String> kVAnnotations = new LinkedHashMap<>();
	private String processId;
	@Singular
	private List<TimelineAnnotation> timelineAnnotations = new ArrayList<>();

	@Override
	public synchronized void stop() {
		if (end == 0) {
			if (begin == 0) {
				throw new IllegalStateException("Span for " + name
						+ " has not been started");
			}
			end = System.currentTimeMillis();
		}
	}

	@Override
	public synchronized long getAccumulatedMillis() {
		if (begin == 0) {
			return 0;
		}
		if (end > 0) {
			return end - begin;
		}
		return System.currentTimeMillis() - begin;
	}

	@Override
	public synchronized boolean isRunning() {
		return begin != 0 && end == 0;
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
