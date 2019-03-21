/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.springframework.cloud.sleuth.instrument.web.ZipkinHttpSpanMapper.URI_HEADER;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;

/**
 * @author Anton Kislitsyn
 */
@RunWith(Parameterized.class)
public class ZipkinHttpSpanMapperTest {

	private static final ZipkinHttpSpanMapper MAPPER = new ZipkinHttpSpanMapper();

	private final String headerName;
	private final String value;
	private final boolean suitable;

	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return Arrays.asList(new Object[] { Span.TRACE_ID_NAME, "traceId", true },
				new Object[] { Span.SPAN_ID_NAME, "spanId", true },
				new Object[] { Span.SPAN_FLAGS, "flags", true },
				new Object[] { Span.PROCESS_ID_NAME, "process", true },
				new Object[] { Span.SPAN_NAME_NAME, "name", true },
				new Object[] { Span.PARENT_ID_NAME, "parent", true },
				new Object[] { Span.SAMPLED_NAME, "sampled", true },
				new Object[] { URI_HEADER, "uri", true },
				new Object[] { UUID.randomUUID().toString(), UUID.randomUUID().toString(),
						false },
				new Object[] { Span.SPAN_ID_NAME, null, false },
				new Object[] { UUID.randomUUID().toString(), null, false });
	}

	public ZipkinHttpSpanMapperTest(String headerName, String value, boolean suitable) {
		this.headerName = headerName;
		this.value = value;
		this.suitable = suitable;
	}

	@Test
	public void should_map_zipkin_suitable_fields() throws Exception {
		Map<String, String> map = MAPPER
				.convert(textMap(Collections.singletonMap(headerName, value)));
		assertThat(map.get(headerName), suitable ? equalTo(value) : is(nullValue()));
	}

	private SpanTextMap textMap(Map<String, String> textMap) {
		return new SpanTextMap() {
			@Override
			public Iterator<Map.Entry<String, String>> iterator() {
				return textMap.entrySet().iterator();
			}

			@Override
			public void put(String key, String value) {
				textMap.put(key, value);
			}
		};
	}
}