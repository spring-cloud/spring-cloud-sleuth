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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.web.servlet.error.ErrorController;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.only;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceHandlerInterceptorTests {

	@Mock BeanFactory beanFactory;
	@InjectMocks TraceHandlerInterceptor traceHandlerInterceptor;

	@Test
	public void should_cache_the_retrieved_bean_when_exception_took_place() throws Exception {
		given(this.beanFactory.getBean(ErrorController.class)).willThrow(new NoSuchBeanDefinitionException("errorController"));

		then(this.traceHandlerInterceptor.errorController()).isNull();
		then(this.traceHandlerInterceptor.errorController()).isNull();
		BDDMockito.then(this.beanFactory).should(only()).getBean(ErrorController.class);
	}

	@Test
	public void should_cache_the_retrieved_bean_when_no_exception_took_place() throws Exception {
		given(this.beanFactory.getBean(ErrorController.class)).willReturn(() -> null);

		then(this.traceHandlerInterceptor.errorController()).isNotNull();
		then(this.traceHandlerInterceptor.errorController()).isNotNull();
		BDDMockito.then(this.beanFactory).should(only()).getBean(ErrorController.class);
	}

}