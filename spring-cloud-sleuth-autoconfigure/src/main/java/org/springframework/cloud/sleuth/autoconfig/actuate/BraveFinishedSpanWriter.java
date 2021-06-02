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
import java.util.stream.Collectors;

import brave.Tags;
import brave.handler.MutableSpanBytesEncoder;

import org.springframework.cloud.sleuth.brave.bridge.BraveFinishedSpan;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.exporter.SpanReporter;

/**
 * A {@link SpanReporter} that buffers finished spans.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
class BraveFinishedSpanWriter implements FinishedSpanWriter {
	@Override
	public String write(TextOutputFormat format, List<FinishedSpan> spans) {
		if (format == TextOutputFormat.CONTENT_TYPE_OPENZIPKIN_JSON_V2) {
			return new String(MutableSpanBytesEncoder.zipkinJsonV2(Tags.ERROR).encodeList(spans.stream().map(BraveFinishedSpan::toBrave).collect(Collectors.toList())));
		}
		return null;
	}
}
