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

package org.springframework.cloud.sleuth.log;

import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanContinuedEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;

import static org.mockito.BDDMockito.then;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * @author Marcin Grzejszczak
 */
public class Slf4jSpanListenerTest {

	Span spanWithNameToBeExcluded = Span.builder().name("Hystrix").build();
	Span spanWithNameNotToBeExcluded = Span.builder().name("Aspect").build();
	String nameExcludingPattern = "^.*Hystrix.*$";
	Logger log = Mockito.mock(Logger.class);
	Slf4jSpanListener slf4jSpanListener = new Slf4jSpanListener(nameExcludingPattern, log);

	@Test
	public void should_log_when_start_event_arrived_and_pattern_doesnt_match_span_name() throws Exception {
		slf4jSpanListener.start(new SpanAcquiredEvent(this, spanWithNameNotToBeExcluded,
				spanWithNameNotToBeExcluded));

		then(log).should(times(2)).trace(anyString(), anyList());
	}

	@Test
	public void should_log_once_when_start_event_arrived_and_pattern_matches_only_parent_span_name() throws Exception {
		slf4jSpanListener.start(new SpanAcquiredEvent(this, spanWithNameToBeExcluded,
				spanWithNameNotToBeExcluded));

		then(log).should().trace(anyString(), anyList());
	}

	@Test
	public void should_log_when_continue_event_arrived_and_pattern_doesnt_match_span_name() throws Exception {
		slf4jSpanListener.continued(new SpanContinuedEvent(this,
				spanWithNameNotToBeExcluded));

		then(log).should().trace(anyString(), anyList());
	}

	@Test
	public void should_not_log_when_continue_event_arrived_and_pattern_matches_name() throws Exception {
		slf4jSpanListener.continued(new SpanContinuedEvent(this, spanWithNameToBeExcluded));

		then(log).should(never()).trace(anyString(), anyList());
	}

	@Test
	public void should_log_when_close_event_arrived_and_pattern_doesnt_match_span_name() throws Exception {
		slf4jSpanListener.stop(new SpanReleasedEvent(this, spanWithNameNotToBeExcluded));

		then(log).should().trace(anyString(), anyList());
	}

	@Test
	public void should_log_both_spans_when_their_names_dont_match_pattern() throws Exception {
		slf4jSpanListener.stop(new SpanReleasedEvent(this, spanWithNameNotToBeExcluded,
				spanWithNameNotToBeExcluded));

		then(log).should(times(2)).trace(anyString(), anyList());
	}

	@Test
	public void should_not_log_any_spans_if_both_match_pattern() throws Exception {
		slf4jSpanListener.stop(new SpanReleasedEvent(this, spanWithNameToBeExcluded,
				spanWithNameToBeExcluded));

		then(log).should(never()).trace(anyString(), anyList());
	}

	@Test
	public void should_log_only_current_span_if_parent_span_name_matches_pattern() throws Exception {
		slf4jSpanListener.stop(new SpanReleasedEvent(this, spanWithNameNotToBeExcluded,
				spanWithNameToBeExcluded));

		then(log).should().trace(anyString(), anyList());
	}

	@Test
	public void should_log_only_current_span_if_there_is_no_parent() throws Exception {
		slf4jSpanListener.stop(new SpanReleasedEvent(this, spanWithNameNotToBeExcluded));

		then(log).should().trace(anyString(), anyList());
	}
}