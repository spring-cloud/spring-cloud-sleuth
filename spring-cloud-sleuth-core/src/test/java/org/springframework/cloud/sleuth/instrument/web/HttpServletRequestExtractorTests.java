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
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.sampler.NeverSampler;

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
				.willReturn(Span.SPAN_SAMPLED);
		BDDMockito.given(this.request.getHeader(Span.B3_NAME))
				.willReturn("0000000000000005-0000000000000004");

		Span span = this.extractor.joinTrace(new HttpServletRequestTextMap(this.request));

		then(span)
				.isNotNull()
				.hasTraceIdEqualTo(5L)
				.hasSpanIdEqualTo(4L)
				.hasParentSpanIdEqualTo(30L)
				.isExportable();
	}

	@Test
	public void should_pick_values_from_b3_with_debug_flag_if_present() {
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
				.willReturn("0000000000000005-0000000000000004-d");

		Span span = this.extractor.joinTrace(new HttpServletRequestTextMap(this.request));

		then(span)
				.isNotNull()
				.hasTraceIdEqualTo(5L)
				.hasSpanIdEqualTo(4L)
				.hasParentSpanIdEqualTo(30L)
				.isExportable();
	}

	@Test
	public void should_pick_values_from_b3_with_sampled_flag_if_present() {
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
				.hasParentSpanIdEqualTo(30L)
				.isExportable();
	}

	@Test
	public void should_pick_values_from_b3_with_parent_id_if_present() {
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
				.willReturn("0000000000000005-0000000000000004-1-0000000000000006");

		Span span = this.extractor.joinTrace(new HttpServletRequestTextMap(this.request));

		then(span)
				.isNotNull()
				.hasTraceIdEqualTo(5L)
				.hasSpanIdEqualTo(4L)
				.hasParentSpanIdEqualTo(6L)
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

	@Test
	public void should_call_sampler_when_trace_id_and_span_id_are_set_and_sampled_flag_is_not() {
		HttpServletRequest request = stubWithoutSampledFlag(Mockito.mock(HttpServletRequest.class));
		Sampler sampler = new FirstFalseThenTrueSampler();

		Span span = extractorWithSampler(sampler)
					.joinTrace(new HttpServletRequestTextMap(request));

		then(span)
				.isNotNull()
				.hasTraceIdEqualTo(10L)
				.hasSpanIdEqualTo(20L)
				.isNotExportable();

		request = stubWithoutSampledFlag(Mockito.mock(HttpServletRequest.class));
		span = extractorWithSampler(sampler)
					.joinTrace(new HttpServletRequestTextMap(request));

		then(span)
				.isNotNull()
				.hasTraceIdEqualTo(10L)
				.hasSpanIdEqualTo(20L)
				.isExportable();
	}

	@Test
	public void should_not_call_sampler_when_trace_id_and_span_id_are_set_and_sampled_flag_is_set_too() {
		HttpServletRequest request = stubWithSampledFlag(Mockito.mock(HttpServletRequest.class));
		Sampler sampler = new NeverSampler();

		Span span = extractorWithSampler(sampler)
					.joinTrace(new HttpServletRequestTextMap(request));

		then(span)
				.isNotNull()
				.hasTraceIdEqualTo(10L)
				.hasSpanIdEqualTo(20L)
				.isExportable();
	}

	private HttpServletRequest stubWithoutSampledFlag(HttpServletRequest request) {
		BDDMockito.given(request.getHeaderNames())
				.willReturn(new Vector<>(Arrays.asList(Span.TRACE_ID_NAME,
						Span.SPAN_ID_NAME)).elements());
		BDDMockito.given(request.getHeader(Span.TRACE_ID_NAME))
				.willReturn(Span.idToHex(10L));
		BDDMockito.given(request.getHeader(Span.SPAN_ID_NAME))
				.willReturn(Span.idToHex(20L));
		BDDMockito.given(request.getRequestURI()).willReturn("http://foo.com");
		BDDMockito.given(request.getContextPath()).willReturn("/");
		return request;
	}

	private HttpServletRequest stubWithSampledFlag(HttpServletRequest request) {
		BDDMockito.given(request.getHeaderNames())
				.willReturn(new Vector<>(Arrays.asList(Span.TRACE_ID_NAME,
						Span.SPAN_ID_NAME, Span.SAMPLED_NAME)).elements());
		BDDMockito.given(request.getHeader(Span.TRACE_ID_NAME))
				.willReturn(Span.idToHex(10L));
		BDDMockito.given(request.getHeader(Span.SPAN_ID_NAME))
				.willReturn(Span.idToHex(20L));
		BDDMockito.given(request.getHeader(Span.SAMPLED_NAME))
				.willReturn(Span.SPAN_SAMPLED);
		BDDMockito.given(request.getRequestURI()).willReturn("http://foo.com");
		BDDMockito.given(request.getContextPath()).willReturn("/");
		return request;
	}

	private ZipkinHttpSpanExtractor extractorWithSampler(Sampler sampler) {
		return new ZipkinHttpSpanExtractor(
				Pattern.compile(""), sampler);
	}
}

class FirstFalseThenTrueSampler implements Sampler {

	int counter = 0;

	@Override public boolean isSampled(Span span) {
		boolean sampled = counter > 0;
		counter++;
		return sampled;
	}
}