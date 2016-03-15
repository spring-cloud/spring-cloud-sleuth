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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Names of default headers that need to be sent between processes
 * for tracing to be operational.
 *
 * Default Zipkin Headers are
 *
 * <ul>
 *     <li>{@code X-B3-TraceId} 64 encoded bits</li>
 *     <li>{@code X-B3-SpanId} 64 encoded bits</li>
 *     <li>{@code X-B3-ParentSpanId} 64 encoded bits</li>
 *     <li>{@code X-B3-Sampled} Boolean (either “1” or “0”)</li>
 * </ul>
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
@ConfigurationProperties("spring.sleuth.headers")
public class TraceHeaders {

	public static final String SPAN_SAMPLED = "1";
	public static final String SPAN_NOT_SAMPLED = "0";

	public static final String ZIPKIN_TRACE_ID_HEADER_NAME = "X-B3-TraceId";
	public static final String ZIPKIN_SPAN_ID_HEADER_NAME = "X-B3-SpanId";
	public static final String ZIPKIN_PARENT_SPAN_ID_HEADER_NAME = "X-B3-ParentSpanId";
	public static final String ZIPKIN_SAMPLED_HEADER_NAME = "X-B3-Sampled";
	public static final String ZIPKIN_PROCESS_ID_HEADER_NAME = "X-Process-Id";

	private String traceId = ZIPKIN_TRACE_ID_HEADER_NAME;
	private String spanId = ZIPKIN_SPAN_ID_HEADER_NAME;
	private String parentSpanId = ZIPKIN_PARENT_SPAN_ID_HEADER_NAME;
	private String sampled = ZIPKIN_SAMPLED_HEADER_NAME;
	private String processId = ZIPKIN_PROCESS_ID_HEADER_NAME;

	public String getTraceId() {
		return this.traceId;
	}

	public void setTraceId(String traceId) {
		this.traceId = traceId;
	}

	public String getSpanId() {
		return this.spanId;
	}

	public void setSpanId(String spanId) {
		this.spanId = spanId;
	}

	public String getParentSpanId() {
		return this.parentSpanId;
	}

	public void setParentSpanId(String parentSpanId) {
		this.parentSpanId = parentSpanId;
	}

	/**
	 * Header name for the header describing whether the Span should be sampled
	 * or not.
	 *
	 * <ul>
	 * <li>"1" - should be sampled</li>
	 * <li>"0" - should NOT be sampled</li>
	 * </ul>
	 *
	 */
	public String getSampled() {
		return this.sampled;
	}

	public void setSampled(String sampled) {
		this.sampled = sampled;
	}

	private Sleuth sleuth = new Sleuth();

	public Sleuth getSleuth() {
		return this.sleuth;
	}

	public void setSleuth(Sleuth sleuth) {
		this.sleuth = sleuth;
	}

	public String getProcessId() {
		return this.processId;
	}

	public void setProcessId(String processId) {
		this.processId = processId;
	}

	public static class Sleuth {

		public static final String SLEUTH_SPAN_NAME_HEADER_NAME = "X-Span-Name";
		public static final String SLEUTH_EXPORTABLE_HEADER_NAME = "X-Span-Export";

		private String spanName = SLEUTH_SPAN_NAME_HEADER_NAME;
		private String exportable = SLEUTH_EXPORTABLE_HEADER_NAME;

		public String getSpanName() {
			return this.spanName;
		}

		public void setSpanName(String spanName) {
			this.spanName = spanName;
		}

		public String getExportable() {
			return this.exportable;
		}

		public void setExportable(String exportable) {
			this.exportable = exportable;
		}
	}
}
