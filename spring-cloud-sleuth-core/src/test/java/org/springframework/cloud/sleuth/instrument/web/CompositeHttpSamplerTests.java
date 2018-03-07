/*
 * Copyright 2013-2018 the original author or authors.
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

import brave.http.HttpAdapter;
import brave.http.HttpSampler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class CompositeHttpSamplerTests {

	@Mock HttpAdapter adapter;
	@Mock HttpSampler left, right;
	HttpSampler sampler;
	Object request = new Object();

	@Before
	public void init(){
		this.sampler = new CompositeHttpSampler(left, right);
	}

	@Test
	public void should_return_null_on_both_null() {
		given(this.left.trySample(this.adapter, this.request)).willReturn(null);
		given(this.right.trySample(this.adapter, this.request)).willReturn(null);

		then(this.sampler.trySample(this.adapter, this.request)).isNull();
	}

	@Test
	public void should_return_false_on_any_false() {
		given(this.left.trySample(this.adapter, this.request)).willReturn(false);
		given(this.right.trySample(this.adapter, this.request)).willReturn(null);

		then(this.sampler.trySample(this.adapter, this.request)).isFalse();

		given(this.left.trySample(this.adapter, this.request)).willReturn(null);
		given(this.right.trySample(this.adapter, this.request)).willReturn(false);

		then(this.sampler.trySample(this.adapter, this.request)).isFalse();

		given(this.left.trySample(this.adapter, this.request)).willReturn(false);
		given(this.right.trySample(this.adapter, this.request)).willReturn(true);

		then(this.sampler.trySample(this.adapter, this.request)).isFalse();

		given(this.left.trySample(this.adapter, this.request)).willReturn(true);
		given(this.right.trySample(this.adapter, this.request)).willReturn(false);

		then(this.sampler.trySample(this.adapter, this.request)).isFalse();
	}

	@Test
	public void should_return_true_on_both_true() {
		given(this.left.trySample(this.adapter, this.request)).willReturn(true);
		given(this.right.trySample(this.adapter, this.request)).willReturn(true);

		then(this.sampler.trySample(this.adapter, this.request)).isTrue();
	}
}
