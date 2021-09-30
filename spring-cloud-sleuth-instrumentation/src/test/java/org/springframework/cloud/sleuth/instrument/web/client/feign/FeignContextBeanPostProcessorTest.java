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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.openfeign.FeignContext;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Hash Jang
 */
@ExtendWith(MockitoExtension.class)
class FeignContextBeanPostProcessorTest {

    @Mock
    BeanFactory beanFactory;

    @Mock
    ApplicationContext parent;

    @Test
    void should_pass_feign_application_context_to_the_trace_representation() {
        FeignContextBeanPostProcessor feignContextBeanPostProcessor = new FeignContextBeanPostProcessor(beanFactory);
        FeignContext feignContext = new FeignContext();
        feignContext.setApplicationContext(parent);
        TraceFeignContext traceFeignContext = (TraceFeignContext) feignContextBeanPostProcessor.postProcessAfterInitialization(feignContext, "test");
        assertThat(traceFeignContext.getParent()).isNotNull();
        assertThat(traceFeignContext.getParent()).isSameAs(parent);
    }

}
