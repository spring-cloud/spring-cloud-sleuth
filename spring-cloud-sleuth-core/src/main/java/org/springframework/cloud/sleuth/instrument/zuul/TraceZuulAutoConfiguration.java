/*
 * Copyright 2013-2015 the original author or authors.
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
package org.springframework.cloud.sleuth.instrument.zuul;

import com.netflix.client.http.HttpRequest;
import com.netflix.zuul.ZuulFilter;

import org.apache.http.client.methods.RequestBuilder;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.netflix.ribbon.support.RibbonRequestCustomizer;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.instrument.web.HttpSpanInjector;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.instrument.web.TraceWebAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import okhttp3.Request;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables span information propagation when using Zuul.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.zuul.enabled", matchIfMissing = true)
@ConditionalOnWebApplication
@ConditionalOnClass(ZuulFilter.class)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(TraceWebAutoConfiguration.class)
public class TraceZuulAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public TracePreZuulFilter tracePreZuulFilter(Tracer tracer,
			HttpSpanInjector spanInjector, HttpTraceKeysInjector httpTraceKeysInjector,
			ErrorParser errorParser) {
		return new TracePreZuulFilter(tracer, spanInjector, httpTraceKeysInjector, errorParser);
	}

	@Bean
	@ConditionalOnMissingBean
	public TracePostZuulFilter tracePostZuulFilter(Tracer tracer, TraceKeys traceKeys) {
		return new TracePostZuulFilter(tracer, traceKeys);
	}

	@Bean
	public static TraceRibbonCommandFactoryBeanPostProcessor traceRibbonCommandFactoryBeanPostProcessor(BeanFactory beanFactory) {
		return new TraceRibbonCommandFactoryBeanPostProcessor(beanFactory);
	}

	@Bean
	@ConditionalOnClass(name = "com.netflix.client.http.HttpRequest.Builder")
	public RibbonRequestCustomizer<HttpRequest.Builder> restClientRibbonRequestCustomizer(Tracer tracer) {
		return new RestClientRibbonRequestCustomizer(tracer);
	}

	@Bean
	@ConditionalOnClass(name = "org.apache.http.client.methods.RequestBuilder")
	public RibbonRequestCustomizer<RequestBuilder> apacheHttpRibbonRequestCustomizer(Tracer tracer) {
		return new ApacheHttpClientRibbonRequestCustomizer(tracer);
	}

	@Bean
	@ConditionalOnClass(name = "okhttp3.Request.Builder")
	public RibbonRequestCustomizer<Request.Builder> okHttpRibbonRequestCustomizer(Tracer tracer) {
		return new OkHttpClientRibbonRequestCustomizer(tracer);
	}

	@Bean
	public static TraceZuulHandlerMappingBeanPostProcessor traceHandlerMappingBeanPostProcessor(BeanFactory beanFactory) {
		return new TraceZuulHandlerMappingBeanPostProcessor(beanFactory);
	}

}
