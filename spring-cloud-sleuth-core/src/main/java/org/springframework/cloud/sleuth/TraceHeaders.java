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
 * Header names representing most important Trace related headers.
 *
 * @since 1.1.0
 */
@ConfigurationProperties("spring.sleuth.headers")
public class TraceHeaders {

	private String traceId = Span.TRACE_ID_NAME;

	private String spanId = Span.SPAN_ID_NAME;

	private String parentId = Span.PARENT_ID_NAME;

	private String sampled = Span.SAMPLED_NAME;

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

	public String getParentId() {
		return this.parentId;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}

	public String getSampled() {
		return this.sampled;
	}

	public void setSampled(String sampled) {
		this.sampled = sampled;
	}
}
