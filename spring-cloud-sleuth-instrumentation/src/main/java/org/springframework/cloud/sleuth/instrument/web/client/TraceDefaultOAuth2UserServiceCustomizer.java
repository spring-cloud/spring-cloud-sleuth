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

import java.lang.reflect.Field;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.instrument.web.mvc.TracingClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * Customizes a {@link DefaultOAuth2UserService} by providing it with a trace interceptor.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.6
 */
public class TraceDefaultOAuth2UserServiceCustomizer {

	private static final Log log = LogFactory.getLog(TraceDefaultOAuth2UserServiceCustomizer.class);

	private final BeanFactory beanFactory;

	private static final Field REST_OPERATIONS = ReflectionUtils.findField(DefaultOAuth2UserService.class,
			"restOperations", RestOperations.class);

	public TraceDefaultOAuth2UserServiceCustomizer(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	public void customize(DefaultOAuth2UserService service) {
		try {
			ReflectionUtils.makeAccessible(Objects.requireNonNull(REST_OPERATIONS,
					"restOperations field was not found in [DefaultOAuth2UserService] class"));
			RestOperations restOperations = (RestOperations) REST_OPERATIONS.get(service);
			if (!(restOperations instanceof RestTemplate)) {
				log.warn(
						"Won't instrument the restOperations field in [DefaultOAuth2UserService] class because it's not a RestTemplate object");
				return;
			}
			RestTemplate template = (RestTemplate) restOperations;
			final TracingClientHttpRequestInterceptor interceptor = this.beanFactory
					.getBean(TracingClientHttpRequestInterceptor.class);
			new RestTemplateInterceptorInjector(interceptor).inject(template);
		}
		catch (Exception e) {
			log.warn("Can't access the restOperations field - won't instrument the [DefaultOAuth2UserService] class",
					e);
		}
	}

}
