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

package org.springframework.cloud.sleuth.instrument.zuul;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceRibbonCommandFactoryBeanPostProcessorTests {

	@Mock RibbonCommandFactory ribbonCommandFactory;
	@Mock BeanFactory beanFactory;
	@InjectMocks TraceRibbonCommandFactoryBeanPostProcessor postProcessor;

	@Test
	public void should_return_a_bean_as_it_is_if_its_not_a_ribbon_command_Factory() {
		then(this.postProcessor.postProcessBeforeInitialization("", "name")).isEqualTo("");
	}

	@Test
	public void should_wrap_ribbon_command_factory_in_a_trace_representation() {
		then(this.postProcessor.postProcessBeforeInitialization(ribbonCommandFactory, "name")).isInstanceOf(
				TraceRibbonCommandFactory.class);
	}
}