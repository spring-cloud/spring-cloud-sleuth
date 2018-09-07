/*
 * Copyright 2013-2017 the original author or authors.
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

import java.util.Arrays;
import java.util.Random;
import java.util.Vector;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

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
	public void should_pick_values_from_b3_if_present() {
		BDDMockito.given(this.request.getHeaderNames())
				.willReturn(new Vector<>(Arrays.asList(Span.B3_NAME, Span.TRACE_ID_NAME,
						Span.SPAN_ID_NAME, Span.PARENT_ID_NAME, Span.SAMPLED_NAME,
						Span.SPAN_FLAGS)).elements());
		BDDMockito.given(this.request.getHeader(Span.TRACE_ID_NAME))
				.willReturn(Span.idToHex(10L));
		BDDMockito.given(this.request.getHeader(Span.SPAN_ID_NAME))
				.willReturn(Span.idToHex(20L));
		BDDMockito.given(this.request.getHeader(Span.PARENT_ID_NAME))
				.willReturn(Span.idToHex(30L));
		BDDMockito.given(this.request.getHeader(Span.SAMPLED_NAME))
				.willReturn(Span.SPAN_NOT_SAMPLED);
		BDDMockito.given(this.request.getHeader(Span.B3_NAME))
				.willReturn("0000000000000005-0000000000000004-1");

		Span span = this.extractor.joinTrace(new HttpServletRequestTextMap(this.request));

		then(span)
				.isNotNull()
				.hasTraceIdEqualTo(5L)
				.hasSpanIdEqualTo(4L)
				.isExportable();
	}

	@Test
	public void should_pick_values_from_old_headers_when_b3_is_invalid() {
		BDDMockito.given(this.request.getHeaderNames())
				.willReturn(new Vector<>(Arrays.asList(Span.B3_NAME, Span.TRACE_ID_NAME,
						Span.SPAN_ID_NAME, Span.PARENT_ID_NAME, Span.SAMPLED_NAME,
						Span.SPAN_FLAGS)).elements());
		BDDMockito.given(this.request.getHeader(Span.TRACE_ID_NAME))
				.willReturn(Span.idToHex(10L));
		BDDMockito.given(this.request.getHeader(Span.SPAN_ID_NAME))
				.willReturn(Span.idToHex(20L));
		BDDMockito.given(this.request.getHeader(Span.PARENT_ID_NAME))
				.willReturn(Span.idToHex(30L));
		BDDMockito.given(this.request.getHeader(Span.SAMPLED_NAME))
				.willReturn(Span.SPAN_NOT_SAMPLED);
		BDDMockito.given(this.request.getHeader(Span.B3_NAME))
				.willReturn("invalid");

		Span span = this.extractor.joinTrace(new HttpServletRequestTextMap(this.request));

		then(span)
				.isNotNull()
				.hasTraceIdEqualTo(10L)
				.hasSpanIdEqualTo(20L)
				.isNotExportable();
	}

	@Test
	public void should_accept_128bit_trace_id() {
		String hex128Bits = spanInHeaders();

		Span span = this.extractor.joinTrace(new HttpServletRequestTextMap(this.request));

		then(span.traceIdString()).isEqualTo(hex128Bits);
	}

	@Test
	public void should_set_shared_flag_for_sampled_span_in_headers() {
		spanInHeaders();

		Span span = this.extractor.joinTrace(new HttpServletRequestTextMap(this.request));

		then(span.isShared()).isTrue();
	}

	@Test
	public void should_not_set_shared_flag_for_non_sampled_span_in_headers() {
		spanInHeaders();
		BDDMockito.given(this.request.getHeader(Span.SAMPLED_NAME))
				.willReturn(Span.SPAN_NOT_SAMPLED);

		Span span = this.extractor.joinTrace(new HttpServletRequestTextMap(this.request));

		then(span.isShared()).isFalse();
	}

	@Test
	public void should_not_set_shared_flag_for_sampled_span_in_headers_without_span_trace_id() {
		BDDMockito.given(this.request.getHeaderNames())
				.willReturn(new Vector<>(Arrays.asList(Span.SPAN_FLAGS, Span.SPAN_ID_NAME)).elements());
		BDDMockito.given(this.request.getHeader(Span.SPAN_FLAGS))
				.willReturn("1");
		BDDMockito.given(this.request.getHeader(Span.SPAN_ID_NAME))
				.willReturn("48485a3953bb6124");

		Span span = this.extractor.joinTrace(new HttpServletRequestTextMap(this.request));

		then(span.isShared()).isFalse();
	}

	private String spanInHeaders() {
		String hex128Bits = "463ac35c9f6413ad48485a3953bb6124";
		String lower64Bits = "48485a3953bb6124";

		BDDMockito.given(this.request.getHeaderNames())
				.willReturn(new Vector<>(Arrays.asList(Span.TRACE_ID_NAME, Span.SPAN_ID_NAME, Span.SAMPLED_NAME)).elements());
		BDDMockito.given(this.request.getHeader(Span.TRACE_ID_NAME))
				.willReturn(hex128Bits);
		BDDMockito.given(this.request.getHeader(Span.SPAN_ID_NAME))
				.willReturn(lower64Bits);
		BDDMockito.given(this.request.getHeader(Span.SAMPLED_NAME))
				.willReturn(Span.SPAN_SAMPLED);
		return hex128Bits;
	}
}
