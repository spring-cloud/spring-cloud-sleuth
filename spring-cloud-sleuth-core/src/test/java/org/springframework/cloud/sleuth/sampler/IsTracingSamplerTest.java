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

package org.springframework.cloud.sleuth.sampler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAccessor;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class IsTracingSamplerTest {

	@Mock SpanAccessor spanAccessor;
	@Mock Span span;
	@InjectMocks IsTracingSampler isTracingSampler;

	@Test
	public void should_sample_when_tracing_is_on() throws Exception {
		given(this.spanAccessor.isTracing()).willReturn(true);

		then(this.isTracingSampler.isSampled(this.span)).isTrue();
	}

	@Test
	public void should_not_sample_when_tracing_is_off() throws Exception {
		given(this.spanAccessor.isTracing()).willReturn(false);

		then(this.isTracingSampler.isSampled(this.span)).isFalse();
	}
}