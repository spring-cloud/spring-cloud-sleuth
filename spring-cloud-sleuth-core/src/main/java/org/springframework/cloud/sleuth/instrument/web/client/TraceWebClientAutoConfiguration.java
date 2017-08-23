/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.PostConstruct;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoRestTemplateCustomizer;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpSpanInjector;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.instrument.web.TraceWebAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables span information propagation when using
 * {@link RestTemplate}
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration
@SleuthWebClientEnabled
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnBean(HttpTraceKeysInjector.class)
@AutoConfigureAfter(TraceWebAutoConfiguration.class)
public class TraceWebClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public TraceRestTemplateInterceptor traceRestTemplateInterceptor(Tracer tracer,
			HttpSpanInjector spanInjector, HttpTraceKeysInjector httpTraceKeysInjector,
			ErrorParser errorParser) {
		return new TraceRestTemplateInterceptor(tracer, spanInjector,
				httpTraceKeysInjector, errorParser);
	}

	@Configuration
	protected static class TraceInterceptorConfiguration {

		@Autowired(required = false)
		private Collection<RestTemplate> restTemplates;

		@Autowired
		private TraceRestTemplateInterceptor traceRestTemplateInterceptor;

		@PostConstruct
		public void init() {
			if (this.restTemplates != null) {
				for (RestTemplate restTemplate : this.restTemplates) {
					new RestTemplateInterceptorInjector(
							this.traceRestTemplateInterceptor).inject(restTemplate);
				}
			}
		}
	}

	@Configuration
	@ConditionalOnClass({ UserInfoRestTemplateCustomizer.class, OAuth2RestTemplate.class })
	protected static class TraceOAuthConfiguration {

		@Autowired BeanFactory beanFactory;

		@Bean
		UserInfoRestTemplateCustomizerBPP userInfoRestTemplateCustomizerBeanPostProcessor() {
			return new UserInfoRestTemplateCustomizerBPP(this.beanFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		UserInfoRestTemplateCustomizer traceUserInfoRestTemplateCustomizer() {
			return new TraceUserInfoRestTemplateCustomizer(this.beanFactory);
		}

		private static class UserInfoRestTemplateCustomizerBPP implements BeanPostProcessor {

			private final BeanFactory beanFactory;

			UserInfoRestTemplateCustomizerBPP(BeanFactory beanFactory) {
				this.beanFactory = beanFactory;
			}

			@Override
			public Object postProcessBeforeInitialization(Object bean,
					String beanName) throws BeansException {
				return bean;
			}

			@Override
			public Object postProcessAfterInitialization(final Object bean,
					String beanName) throws BeansException {
				final BeanFactory beanFactory = this.beanFactory;
				if (bean instanceof UserInfoRestTemplateCustomizer &&
						!(bean instanceof TraceUserInfoRestTemplateCustomizer)) {
					return new TraceUserInfoRestTemplateCustomizer(beanFactory, bean);
				}
				return bean;
			}
		}
	}
}

class RestTemplateInterceptorInjector {
	private final TraceRestTemplateInterceptor interceptor;

	RestTemplateInterceptorInjector(TraceRestTemplateInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	void inject(RestTemplate restTemplate) {
		List<ClientHttpRequestInterceptor> interceptors = new ArrayList<ClientHttpRequestInterceptor>(
				restTemplate.getInterceptors());
		interceptors.add(this.interceptor);
		restTemplate.setInterceptors(interceptors);
	}
}

class TraceUserInfoRestTemplateCustomizer implements UserInfoRestTemplateCustomizer {

	private final BeanFactory beanFactory;
	private final Object delegate;

	TraceUserInfoRestTemplateCustomizer(BeanFactory beanFactory) {
		this(beanFactory, null);
	}

	TraceUserInfoRestTemplateCustomizer(BeanFactory beanFactory, Object bean) {
		this.beanFactory = beanFactory;
		this.delegate = bean;
	}

	@Override public void customize(OAuth2RestTemplate template) {
		final TraceRestTemplateInterceptor interceptor =
				this.beanFactory.getBean(TraceRestTemplateInterceptor.class);
		new RestTemplateInterceptorInjector(interceptor).inject(template);
		if (this.delegate != null) {
			((UserInfoRestTemplateCustomizer) this.delegate).customize(template);
		}
	}
}