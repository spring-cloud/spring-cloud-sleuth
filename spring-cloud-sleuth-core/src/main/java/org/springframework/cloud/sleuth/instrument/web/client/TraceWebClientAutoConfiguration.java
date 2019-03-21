/*
 * Copyright 2013-2017 the original author or authors.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpSpanInjector;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.instrument.web.TraceWebAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
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

		@Autowired
		private TraceRestTemplateInterceptor traceRestTemplateInterceptor;

		@Bean
		@Order
		RestTemplateCustomizer traceRestTemplateCustomizer() {
			return new TraceRestTemplateCustomizer(this.traceRestTemplateInterceptor);
		}

		@Bean static TraceRestTemplateBeanPostProcessor traceRestTemplateBPP(BeanFactory beanFactory) {
			return new TraceRestTemplateBeanPostProcessor(beanFactory);
		}
	}

	@Configuration
	@ConditionalOnClass({ UserInfoRestTemplateCustomizer.class, OAuth2RestTemplate.class })
	protected static class TraceOAuthConfiguration {

		@Bean static UserInfoRestTemplateCustomizerBeanPostProcessor userInfoRestTemplateCustomizerBeanPostProcessor(BeanFactory beanFactory) {
			return new UserInfoRestTemplateCustomizerBeanPostProcessor(beanFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		UserInfoRestTemplateCustomizer traceUserInfoRestTemplateCustomizer(BeanFactory beanFactory) {
			return new TraceUserInfoRestTemplateCustomizer(beanFactory);
		}

		private static class UserInfoRestTemplateCustomizerBeanPostProcessor implements BeanPostProcessor {

			private final BeanFactory beanFactory;

			UserInfoRestTemplateCustomizerBeanPostProcessor(BeanFactory beanFactory) {
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
	private final ClientHttpRequestInterceptor interceptor;

	RestTemplateInterceptorInjector(ClientHttpRequestInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	void inject(RestTemplate restTemplate) {
		if (hasTraceInterceptor(restTemplate)) {
			return;
		}
		List<ClientHttpRequestInterceptor> interceptors = new ArrayList<ClientHttpRequestInterceptor>(
				restTemplate.getInterceptors());
		interceptors.add(0, this.interceptor);
		restTemplate.setInterceptors(interceptors);
	}

	private boolean hasTraceInterceptor(RestTemplate restTemplate) {
		for (ClientHttpRequestInterceptor interceptor : restTemplate
				.getInterceptors()) {
			if (interceptor instanceof TraceRestTemplateInterceptor) {
				return true;
			}
		}
		return false;
	}
}

class TraceRestTemplateCustomizer implements RestTemplateCustomizer {

	private final TraceRestTemplateInterceptor interceptor;

	TraceRestTemplateCustomizer(TraceRestTemplateInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@Override public void customize(RestTemplate restTemplate) {
		new RestTemplateInterceptorInjector(this.interceptor)
				.inject(restTemplate);
	}
}

class TraceRestTemplateBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;
	private TraceRestTemplateInterceptor interceptor;

	TraceRestTemplateBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	@Override public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof RestTemplate)  {
			RestTemplate rt = (RestTemplate) bean;
			new RestTemplateInterceptorInjector(interceptor()).inject(rt);
		}
		return bean;
	}

	private ClientHttpRequestInterceptor interceptor() {
		return new LazyTracingClientHttpRequestInterceptor(this.beanFactory);
	}

}

class LazyTracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

	private final BeanFactory beanFactory;
	private TraceRestTemplateInterceptor interceptor;

	public LazyTracingClientHttpRequestInterceptor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override public ClientHttpResponse intercept(HttpRequest request, byte[] body,
			ClientHttpRequestExecution execution) throws IOException {
		return interceptor().intercept(request, body, execution);
	}

	private TraceRestTemplateInterceptor interceptor() {
		if (this.interceptor == null) {
			this.interceptor = this.beanFactory.getBean(TraceRestTemplateInterceptor.class);
		}
		return this.interceptor;
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