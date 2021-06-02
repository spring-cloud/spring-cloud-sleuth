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

import java.util.List;

import org.jetbrains.annotations.NotNull;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.http.HttpStatus;

/**
 * {@link Endpoint @Endpoint} that outputs spans in a format that can be scraped by a collector.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
@WebEndpoint(id = "traces")
public class TracesScrapeEndpoint {

	private final BufferingSpanReporter bufferingSpanReporter;

	private final FinishedSpanWriter finishedSpanWriter;

	public TracesScrapeEndpoint(BufferingSpanReporter bufferingSpanReporter, FinishedSpanWriter finishedSpanWriter) {
		this.bufferingSpanReporter = bufferingSpanReporter;
		this.finishedSpanWriter = finishedSpanWriter;
	}

	@ReadOperation(producesFrom = TextOutputFormat.class)
	public WebEndpointResponse<String> spansSnapshot(TextOutputFormat format) {
		List<FinishedSpan> finishedSpans = this.bufferingSpanReporter.getFinishedSpans();
		return response(format, finishedSpans);
	}

	@NotNull
	private WebEndpointResponse<String> response(TextOutputFormat format, List<FinishedSpan> finishedSpans) {
		String spans = this.finishedSpanWriter.write(format, finishedSpans);
		if (spans == null) {
			return new WebEndpointResponse<>("The format [" + format.getProducedMimeType().toString()  + " ] is not supported", HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
		}
		return new WebEndpointResponse<>(spans, format);
	}

	@WriteOperation(producesFrom = TextOutputFormat.class)
	public WebEndpointResponse<String> spans(TextOutputFormat format) {
		List<FinishedSpan> finishedSpans = this.bufferingSpanReporter.drainFinishedSpans();
		return response(format, finishedSpans);
	}
}
