/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.instrument.web;

import java.util.regex.Pattern;

import brave.http.HttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@ExtendWith(MockitoExtension.class)
public class SkipPatternSamplerTests {

	@Mock
	HttpRequest request;

	@Test
	public void should_delegate_sampling_decision_if_pattern_is_not_matched() {
		BDDMockito.given(this.request.path()).willReturn("url");
		SkipPatternSampler sampler = new SkipPatternSampler() {
			@Override
			Pattern getPattern() {
				return Pattern.compile("foo");
			}
		};

		then(sampler.trySample(this.request)).isNull();
	}

	@Test
	public void should_not_sample_if_pattern_is_matched() {
		BDDMockito.given(this.request.path()).willReturn("url");
		SkipPatternSampler sampler = new SkipPatternSampler() {
			@Override
			Pattern getPattern() {
				return Pattern.compile(".*");
			}
		};

		then(sampler.trySample(this.request)).isFalse();
	}

	@Test
	public void should_not_get_pattern_twice() {
		BDDMockito.given(this.request.path()).willReturn("url");
		SkipPatternSampler sampler = new SkipPatternSampler() {
			boolean provisioned;

			@Override
			Pattern getPattern() {
				if (provisioned) {
					throw new AssertionError("called twice!");
				}
				try {
					return Pattern.compile(".*");
				}
				finally {
					provisioned = true;
				}
			}
		};

		then(sampler.trySample(this.request)).isFalse();
		then(sampler.trySample(this.request)).isFalse();
	}

}
