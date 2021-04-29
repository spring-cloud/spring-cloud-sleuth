/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import feign.Client;
import feign.Feign;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;

import static org.mockito.Mockito.mock;

/**
 * @author Julien Baillagou
 */
@ExtendWith(MockitoExtension.class)
public class SleuthFeignBuilderTests {

	@Mock
	BeanFactory beanFactory;

	@Test
	public void should_generate_feign_builder() {
		BDDAssertions.then(SleuthFeignBuilder.builder(beanFactory)).isExactlyInstanceOf(Feign.Builder.class);
	}

	@Test
	public void should_generate_feign_builder_with_given_delegate() {
		BDDAssertions.then(SleuthFeignBuilder.builder(beanFactory, mock(Client.class)))
				.isExactlyInstanceOf(Feign.Builder.class);
	}

}
