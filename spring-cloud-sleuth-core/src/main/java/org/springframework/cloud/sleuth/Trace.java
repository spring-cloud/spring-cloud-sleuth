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

import java.util.Arrays;
import java.util.List;

import lombok.Value;
import lombok.experimental.NonFinal;

/**
 * @author Spencer Gibb
 */
@Value
@NonFinal
public class Trace {

	public static final String NOT_SAMPLED_NAME = "X-Not-Sampled";

	public static final String PROCESS_ID_NAME = "X-Process-Id";

	public static final String PARENT_ID_NAME = "X-Parent-Id";

	public static final String TRACE_ID_NAME = "X-Trace-Id";

	public static final String SPAN_NAME_NAME = "X-Span-Name";

	public static final String SPAN_ID_NAME = "X-Span-Id";

	public static final List<String> HEADERS = Arrays.asList(SPAN_ID_NAME, TRACE_ID_NAME,
	SPAN_NAME_NAME, PARENT_ID_NAME, PROCESS_ID_NAME, NOT_SAMPLED_NAME);

	/**
	 * the span for this trace
	 */
	private final Span span;

	/**
	 * the trace that was "current" before this trace was entered
	 */
	private final Trace savedTrace;

	public Trace(Trace saved, Span span) {
		this.savedTrace = saved;
		this.span = span;
	}

	public Trace(Span span) {
		this(null, span);
	}

	public void addAnnotation(String key, String value) {
		if (this.span != null) {
			this.span.addAnnotation(key, value);
		}
	}

}
