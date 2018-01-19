/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

/**
 * Contains trace related messaging headers. The deprecated headers contained `-` which
 * for example in the JMS specs is invalid. That's why the public constants in this class
 * represent the valid names for the tracing headers.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.4
 */
public class TraceMessageHeaders {

	public static final String SPAN_ID_NAME = "spanId";
	public static final String SAMPLED_NAME = "spanSampled";
	public static final String PARENT_ID_NAME = "spanParentSpanId";
	public static final String TRACE_ID_NAME = "spanTraceId";
	public static final String SPAN_NAME_NAME = "spanName";
	public static final String SPAN_FLAGS_NAME = "spanFlags";

	private TraceMessageHeaders() {}
}
