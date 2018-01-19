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

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC Adapter that adds the {@link TraceHandlerInterceptor}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.3
 */
@Configuration
class TraceWebMvcConfigurer implements WebMvcConfigurer {
	@Autowired BeanFactory beanFactory;

	@Bean
	public TraceHandlerInterceptor traceHandlerInterceptor(BeanFactory beanFactory) {
		return new TraceHandlerInterceptor(beanFactory);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this.beanFactory.getBean(TraceHandlerInterceptor.class));
	}
}
