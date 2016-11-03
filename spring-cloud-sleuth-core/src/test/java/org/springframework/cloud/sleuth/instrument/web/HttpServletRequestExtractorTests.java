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

package org.springframework.cloud.sleuth.instrument.web;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Random;
import java.util.Vector;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Span;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(MockitoJUnitRunner.class)
public class HttpServletRequestExtractorTests {

	@Mock HttpServletRequest request;
	ZipkinHttpSpanExtractor extractor = new ZipkinHttpSpanExtractor(
			Pattern.compile(""));

	@Before
	public void setup() {
		BDDMockito.given(this.request.getRequestURI()).willReturn("http://foo.com");
		BDDMockito.given(this.request.getContextPath()).willReturn("/");
	}

	@Test
	public void should_return_null_if_there_is_no_trace_id() {
		then(extractor.joinTrace(new HttpServletRequestTextMap(this.request))).isNull();
	}

	@Test
	public void should_set_random_traceid_if_header_value_is_invalid() {
		BDDMockito.given(this.request.getHeader(Span.TRACE_ID_NAME))
				.willReturn("invalid");

		then(this.extractor.joinTrace(new HttpServletRequestTextMap(this.request))).isNull();
	}

	@Test
	public void should_set_random_spanid_if_header_value_is_invalid() {
		BDDMockito.given(this.request.getHeader(Span.TRACE_ID_NAME))
				.willReturn(String.valueOf(new Random().nextLong()));
		BDDMockito.given(this.request.getHeader(Span.SPAN_ID_NAME))
				.willReturn("invalid");

		then(this.extractor.joinTrace(new HttpServletRequestTextMap(this.request))).isNull();
	}

	@Test
	public void should_not_throw_exception_if_parent_id_is_invalid() {
		BDDMockito.given(this.request.getHeader(Span.TRACE_ID_NAME))
				.willReturn(String.valueOf(new Random().nextLong()));
		BDDMockito.given(this.request.getHeader(Span.SPAN_ID_NAME))
				.willReturn(String.valueOf(new Random().nextLong()));
		BDDMockito.given(this.request.getHeader(Span.PARENT_ID_NAME))
				.willReturn("invalid");

		then(this.extractor.joinTrace(new HttpServletRequestTextMap(this.request))).isNull();
	}

	@Test
	public void should_downgrade_128bit_trace_id_by_dropping_high_bits() {
		String hex128Bits = "463ac35c9f6413ad48485a3953bb6124";
		String lower64Bits = "48485a3953bb6124";

		BDDMockito.given(this.request.getHeaderNames())
				.willReturn(new Vector<>(Arrays.asList(Span.TRACE_ID_NAME, Span.SPAN_ID_NAME)).elements());
		BDDMockito.given(this.request.getHeader(Span.TRACE_ID_NAME))
				.willReturn(hex128Bits);
		BDDMockito.given(this.request.getHeader(Span.SPAN_ID_NAME))
				.willReturn(lower64Bits);

		Span span = this.extractor.joinTrace(new HttpServletRequestTextMap(this.request));

		then(span.getTraceId()).isEqualTo(Span.hexToId(lower64Bits));
	}
}
