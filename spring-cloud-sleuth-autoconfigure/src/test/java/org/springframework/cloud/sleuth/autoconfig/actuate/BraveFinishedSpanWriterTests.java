/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.autoconfig.actuate;

import java.util.Collections;

import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.brave.bridge.BraveFinishedSpan;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;

import static org.assertj.core.api.BDDAssertions.then;

class BraveFinishedSpanWriterTests {

	@Test
	void should_convert_finished_spans_to_zipkin_json() {
		FinishedSpan finishedSpan = new BraveFinishedSpan(
				new MutableSpan(TraceContext.newBuilder().spanId(1L).traceId(2L).build(), null));

		String json = new BraveFinishedSpanWriter().write(TextOutputFormat.CONTENT_TYPE_OPENZIPKIN_JSON_V2,
				Collections.singletonList(finishedSpan));

		then(json).isEqualTo("[{\"traceId\":\"0000000000000002\",\"id\":\"0000000000000001\"}]");
	}

	@Test
	void should_not_support_any_other_format_than_openzipkin() {
		FinishedSpan finishedSpan = new BraveFinishedSpan(
				new MutableSpan(TraceContext.newBuilder().spanId(1L).traceId(2L).build(), null));

		String json = new BraveFinishedSpanWriter().write(TextOutputFormat.CONTENT_TYPE_OTLP_PROTOBUF,
				Collections.singletonList(finishedSpan));

		then(json).isNull();
	}

	@Test
	void should_return_null_when_format_not_supported() {
		then(new BraveFinishedSpanWriter().write(null, Collections.emptyList())).isNull();
	}

}
