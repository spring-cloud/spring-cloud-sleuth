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

package org.springframework.cloud.sleuth.log;

import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.OutputCapture;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.util.StringUtils;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Spencer Gibb
 */
public class JsonLogSpanListenerTests {
	@Rule
	public final OutputCapture output = new OutputCapture();

	@Test
	public void jsonSpanIsOnOneLine() throws IOException {
		JsonLogSpanListener listener = new JsonLogSpanListener();
		Span span = Span.builder()
				.name("testSpan")
				.spanId(1L)
				.parent(2L)
				.traceId(3L)
				.begin(1)
				.end(10)
				.build();
		span.tag("myKey", "myVal");
		span.logEvent("myTimelineAnnotation");
		listener.stop(new SpanReleasedEvent(this, span));

		String output = this.output.toString().trim();
		assertTrue("output doesn't contain prefix", output.contains(listener.getPrefix()));
		assertTrue("output doesn't contain suffix", output.contains(listener.getSuffix()));

		int prefixIndex = output.indexOf(listener.getPrefix());
		int suffixIndex = output.indexOf(listener.getSuffix());
		String json = output.substring(prefixIndex + listener.getPrefix().length(), suffixIndex);
		assertTrue("json is empty", StringUtils.hasText(json));
		assertFalse("json contains linefeed", output.contains("\n"));
		assertFalse("json contains carriage return", output.contains("\r"));

		Span read = listener.getObjectMapper().readValue(json, Span.class);
		assertEquals("span not equals", read, span);
	}
}
