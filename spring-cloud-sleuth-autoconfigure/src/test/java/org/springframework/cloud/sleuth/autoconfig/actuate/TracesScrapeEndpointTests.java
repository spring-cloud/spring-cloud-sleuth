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
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;

import static org.assertj.core.api.BDDAssertions.then;

class TracesScrapeEndpointTests {

	@Test
	void should_return_not_acceptable_when_no_finished_span_writer_is_applicable() {
		TracesScrapeEndpoint tracesScrapeEndpoint = new TracesScrapeEndpoint(bufferingSpanReporter(),
				(format, spans) -> null);

		WebEndpointResponse<Object> response = tracesScrapeEndpoint
				.spansSnapshot(TextOutputFormat.CONTENT_TYPE_OPENZIPKIN_JSON_V2);

		then(response.getStatus()).isEqualTo(HttpStatus.NOT_ACCEPTABLE.value());
	}

	@NonNull
	private BufferingSpanReporter bufferingSpanReporter() {
		return new BufferingSpanReporter(1) {
			@Override
			public List<FinishedSpan> getFinishedSpans() {
				return Collections.emptyList();
			}
		};
	}

}
