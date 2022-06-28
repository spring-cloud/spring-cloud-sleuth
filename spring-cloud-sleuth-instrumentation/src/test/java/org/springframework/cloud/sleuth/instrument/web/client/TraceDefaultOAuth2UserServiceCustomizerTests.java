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

package org.springframework.cloud.sleuth.instrument.web.client;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.sleuth.instrument.web.mvc.TracingClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.client.RestTemplate;

class TraceDefaultOAuth2UserServiceCustomizerTests {

	@Test
	void should_add_a_trace_interceptor_to_defaultoauth2userservice() {
		DefaultOAuth2UserService service = new DefaultOAuth2UserService();
		BeanFactory beanFactory = beanFactory();
		TraceDefaultOAuth2UserServiceCustomizer serviceCustomizer = new TraceDefaultOAuth2UserServiceCustomizer(
				beanFactory);

		serviceCustomizer.customize(service);
		serviceCustomizer.customize(service);

		Object operations = ReflectionUtils
				.getField(ReflectionUtils.findField(DefaultOAuth2UserService.class, "restOperations"), service);
		BDDAssertions.then(operations).isInstanceOf(RestTemplate.class);
		RestTemplate restTemplate = (RestTemplate) operations;
		BDDAssertions.then(restTemplate.getInterceptors()).hasSize(1)
				.hasOnlyElementsOfType(TracingClientHttpRequestInterceptor.class);
	}

	private BeanFactory beanFactory() {
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
		beanFactory.addBean("TracingClientHttpRequestInterceptor",
				TracingClientHttpRequestInterceptor.create(null, null));
		return beanFactory;
	}

}
