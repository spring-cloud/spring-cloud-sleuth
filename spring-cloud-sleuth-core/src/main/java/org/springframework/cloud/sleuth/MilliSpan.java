/*
 * Copyright 2013-2015 the original author or authors.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

/**
 * @author Spencer Gibb
 */
@Data
@Builder
public class MilliSpan implements Span {
	private final long begin;
	private long end = 0;
	private final String name;
	private final String traceId;
	@Singular
	private List<String> parents;
	private final String spanId;
	private boolean remote = false;
	private final Map<String, String> kVAnnotations = new LinkedHashMap<>();
	private final String processId;
	@Singular
	private final List<TimelineAnnotation> timelineAnnotations = new ArrayList<>();

	public MilliSpan(long begin, long end, String name, String traceId, List<String> parents, String spanId, boolean remote, String processId) {
		this.begin = begin;
		this.end = end;
		this.name = name;
		this.traceId = traceId;
		this.parents = parents;
		this.spanId = spanId;
		this.remote = remote;
		this.processId = processId;
	}

	//for serialization
	@SuppressWarnings("unused")
	private MilliSpan() {
		this.begin = 0;
		this.name = null;
		this.traceId = null;
		this.parents = null;
		this.spanId = null;
		this.processId = null;
	}

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
	public void addAnnotation(String key, String value) {
		this.kVAnnotations.put(key, value);
	}

	@Override
	public void addTimelineAnnotation(String msg) {
		this.timelineAnnotations.add(new TimelineAnnotation(System.currentTimeMillis(),
				msg));
	}

}
