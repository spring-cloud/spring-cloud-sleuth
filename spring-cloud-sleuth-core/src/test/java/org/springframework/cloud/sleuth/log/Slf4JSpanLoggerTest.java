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

package org.springframework.cloud.sleuth.log;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.cloud.sleuth.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * @author Marcin Grzejszczak
 */
public class Slf4JSpanLoggerTest {

	Span spanWithNameToBeExcluded = Span.builder().name("Hystrix").build();
	Span spanWithNameNotToBeExcluded = Span.builder().name("Aspect").parent(3L).build();
	String nameExcludingPattern = "^.*Hystrix.*$";
	Logger log = Mockito.mock(Logger.class);
	Slf4jSpanLogger slf4JSpanLogger = new Slf4jSpanLogger(this.nameExcludingPattern, this.log);

	@Before
	public void setup() {
		MDC.clear();
		given(log.isTraceEnabled()).willReturn(true);
	}

	@Test
	public void when_start_event_arrived_should_add_64bit_trace_id_to_MDC() throws Exception {
		Span span = Span.builder().traceId(1L).spanId(2L).build();

		this.slf4JSpanLogger.logStartedSpan(this.spanWithNameNotToBeExcluded, span);

		assertThat(MDC.get("X-B3-TraceId")).isEqualTo("0000000000000001");
	}

	@Test
	public void when_start_event_arrived_should_add_128bit_trace_id_to_MDC() throws Exception {
		Span span = Span.builder().traceIdHigh(1L).traceId(2L).spanId(3L).build();

		this.slf4JSpanLogger.logStartedSpan(this.spanWithNameNotToBeExcluded, span);

		assertThat(MDC.get("X-B3-TraceId")).isEqualTo("00000000000000010000000000000002");
	}

	@Test
	public void when_continued_event_arrived_should_add_64bit_trace_id_to_MDC() throws Exception {
		Span span = Span.builder().traceId(1L).spanId(2L).build();

		this.slf4JSpanLogger.logContinuedSpan(span);

		assertThat(MDC.get("X-B3-TraceId")).isEqualTo("0000000000000001");
	}

	@Test
	public void when_continued_event_arrived_should_add_128bit_trace_id_to_MDC() throws Exception {
		Span span = Span.builder().traceIdHigh(1L).traceId(2L).spanId(3L).build();

		this.slf4JSpanLogger.logContinuedSpan(span);

		assertThat(MDC.get("X-B3-TraceId")).isEqualTo("00000000000000010000000000000002");
	}

	@Test
	public void should_log_when_start_event_arrived_and_pattern_doesnt_match_span_name() throws Exception {
		this.slf4JSpanLogger.logStartedSpan(this.spanWithNameNotToBeExcluded,
				this.spanWithNameNotToBeExcluded);

		then(this.log).should(times(2)).trace(anyString(), anyList());
	}

	@Test
	public void should_log_once_when_start_event_arrived_and_pattern_matches_only_parent_span_name() throws Exception {
		this.slf4JSpanLogger.logStartedSpan(this.spanWithNameToBeExcluded,
				this.spanWithNameNotToBeExcluded);

		then(this.log).should().trace(anyString(), anyList());
	}

	@Test
	public void should_log_when_continue_event_arrived_and_pattern_doesnt_match_span_name() throws Exception {
		this.slf4JSpanLogger.logContinuedSpan(
				this.spanWithNameNotToBeExcluded);

		then(this.log).should().trace(anyString(), anyList());
	}

	@Test
	public void should_not_log_when_continue_event_arrived_and_pattern_matches_name() throws Exception {
		this.slf4JSpanLogger.logContinuedSpan(this.spanWithNameToBeExcluded);

		then(this.log).should(never()).trace(anyString(), anyList());
	}

	@Test
	public void should_log_when_close_event_arrived_and_pattern_doesnt_match_span_name() throws Exception {
		this.slf4JSpanLogger.logStoppedSpan(null, this.spanWithNameNotToBeExcluded);

		then(this.log).should().trace(anyString(), anyList());
	}

	@Test
	public void should_log_both_spans_when_their_names_dont_match_pattern() throws Exception {
		this.slf4JSpanLogger.logStoppedSpan(this.spanWithNameNotToBeExcluded,
				this.spanWithNameNotToBeExcluded);

		then(this.log).should(times(2)).trace(anyString(), anyList());
	}

	@Test
	public void should_not_log_any_spans_if_both_match_pattern() throws Exception {
		this.slf4JSpanLogger.logStoppedSpan(this.spanWithNameToBeExcluded,
				this.spanWithNameToBeExcluded);

		then(this.log).should(never()).trace(anyString(), anyList());
	}

	@Test
	public void should_log_only_current_span_if_parent_span_name_matches_pattern() throws Exception {
		this.slf4JSpanLogger.logStoppedSpan(this.spanWithNameNotToBeExcluded,
				this.spanWithNameToBeExcluded);

		then(this.log).should().trace(anyString(), anyList());
	}

	@Test
	public void should_log_only_current_span_if_there_is_no_parent() throws Exception {
		this.slf4JSpanLogger.logStoppedSpan(null, this.spanWithNameNotToBeExcluded);

		then(this.log).should().trace(anyString(), anyList());
	}

	@Test
	public void should_set_mdc_entry_for_parent_when_starting() throws Exception {
		this.slf4JSpanLogger.logStartedSpan(this.spanWithNameNotToBeExcluded,
				this.spanWithNameNotToBeExcluded);

		assertThat(MDC.get(Span.PARENT_ID_NAME)).isEqualTo(
				Span.idToHex(this.spanWithNameNotToBeExcluded.getSpanId()));
	}

	@Test
	public void should_set_mdc_entry_for_parent_when_continuing() throws Exception {
  		this.slf4JSpanLogger.logContinuedSpan(this.spanWithNameNotToBeExcluded);

		assertThat(MDC.get(Span.PARENT_ID_NAME)).isEqualTo(Span.idToHex(3L));
	}

	@Test
	public void should_set_mdc_entry_for_parent_when_stopping() throws Exception {
		this.slf4JSpanLogger.logStoppedSpan(this.spanWithNameNotToBeExcluded,
				this.spanWithNameNotToBeExcluded);

		assertThat(MDC.get(Span.PARENT_ID_NAME)).isEqualTo(Span.idToHex(3L));
	}
}