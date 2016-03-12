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

	private String traceId = "X-Trace-Id";
	private String spanId = "X-Span-Id";
	private String parentSpanId = "X-Parent-Id";
	private String notSampled = "X-Not-Sampled";
	private String processId = "X-Process-Id";
	private String spanName = "X-Span-Name";
	private String exportable = "X-Span-Export";

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

	public String getNotSampled() {
		return this.notSampled;
	}

	public void setNotSampled(String notSampled) {
		this.notSampled = notSampled;
	}

	public String getProcessId() {
		return this.processId;
	}

	public void setProcessId(String processId) {
		this.processId = processId;
	}

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
