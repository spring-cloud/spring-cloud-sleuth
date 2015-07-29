package org.springframework.cloud.sleuth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;
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
	private final String traceId;
	@Singular
	private List<String> parents;
	private final String spanId;
	private Map<String, String> kVAnnotations = new LinkedHashMap<>();
	private final String processId;
	@Singular
	private List<TimelineAnnotation> timelineAnnotations = new ArrayList<>();

	@Override
	public synchronized void stop() {
		if (this.end == 0) {
			if (this.begin == 0) {
				throw new IllegalStateException("Span for " + this.name
						+ " has not been started");
			}
			this.end = System.currentTimeMillis();
		}
	}

	@Override
	public synchronized long getAccumulatedMillis() {
		if (this.begin == 0) {
			return 0;
		}
		if (this.end > 0) {
			return this.end - this.begin;
		}
		return System.currentTimeMillis() - this.begin;
	}

	@Override
	public synchronized boolean isRunning() {
		return this.begin != 0 && this.end == 0;
	}

	@Override
	public void addKVAnnotation(String key, String value) {
		this.kVAnnotations.put(key, value);
	}

	@Override
	public void addTimelineAnnotation(String msg) {
		this.timelineAnnotations.add(new TimelineAnnotation(System.currentTimeMillis(), msg));
	}

}
