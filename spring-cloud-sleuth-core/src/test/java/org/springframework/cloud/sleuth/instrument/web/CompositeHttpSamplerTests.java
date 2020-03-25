/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.stream.Stream;

import brave.http.HttpRequest;
import brave.sampler.SamplerFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.BDDAssertions.then;

@ExtendWith(MockitoExtension.class)
public class CompositeHttpSamplerTests {

	@Mock
	HttpRequest request;

	@Test
	public void should_return_null_on_both_null() {
		then(new CompositeHttpSampler(new NullSampler(), new NullSampler()).trySample(this.request)).isNull();
	}

	@ParameterizedTest
	@MethodSource("falseArgs")
	void should_return_false_on_any_false(SamplerFunction<HttpRequest> left, SamplerFunction<HttpRequest> right) {
		then(new CompositeHttpSampler(left, right).trySample(this.request)).isFalse();
	}

	private static Stream<Arguments> falseArgs() {
		return Stream.of(Arguments.of(new NullSampler(), new FalseSampler()),
				Arguments.of(new FalseSampler(), new TrueSampler()),
				Arguments.of(new TrueSampler(), new FalseSampler()));
	}

	@Test
	public void should_return_true_on_both_true() {
		then(new CompositeHttpSampler(new TrueSampler(), new TrueSampler()).trySample(this.request)).isTrue();
	}

}

class TrueSampler implements SamplerFunction<HttpRequest> {

	@Override
	public Boolean trySample(HttpRequest arg) {
		return true;
	}

	@Override
	public String toString() {
		return "True";
	}

}

class FalseSampler implements SamplerFunction<HttpRequest> {

	@Override
	public Boolean trySample(HttpRequest arg) {
		return false;
	}

	@Override
	public String toString() {
		return "False";
	}

}

class NullSampler implements SamplerFunction<HttpRequest> {

	@Override
	public Boolean trySample(HttpRequest arg) {
		return null;
	}

	@Override
	public String toString() {
		return "Null";
	}

}