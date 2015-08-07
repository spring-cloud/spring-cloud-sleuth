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

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.OutputCapture;
import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.event.SpanStoppedEvent;

import java.io.IOException;

/**
 * @author Spencer Gibb
 */
public class JsonLogSpanListenerTests {
	@Rule
	public final OutputCapture output = new OutputCapture();

	@Test
	public void jsonSpanIsOnOneLine() throws IOException {
		JsonLogSpanListener listener = new JsonLogSpanListener();
		Span span = MilliSpan.builder()
				.name("testSpan")
				.spanId("spanId1")
				.parent("parentId1")
				.traceId("traceId1")
				.begin(1)
				.end(10)
				.build();
		span.addKVAnnotation("myKey", "myVal");
		span.addTimelineAnnotation("myTimelineAnnotation");
		listener.stop(new SpanStoppedEvent(this, span));

		String output = this.output.toString().trim();
		assertTrue("output doesn't contain prefix", output.contains(listener.getPrefix()));
		assertTrue("output doesn't contain suffix", output.contains(listener.getSuffix()));

		int prefixIndex = output.indexOf(listener.getPrefix());
		int suffixIndex = output.indexOf(listener.getSuffix());
		String json = output.substring(prefixIndex + listener.getPrefix().length(), suffixIndex);
		assertFalse("json contains linefeed", output.contains("\n"));
		assertFalse("json contains carriage return", output.contains("\r"));

		MilliSpan read = listener.getObjectMapper().readValue(json, MilliSpan.class);
		assertEquals("span not equals", read, span);
	}
}
