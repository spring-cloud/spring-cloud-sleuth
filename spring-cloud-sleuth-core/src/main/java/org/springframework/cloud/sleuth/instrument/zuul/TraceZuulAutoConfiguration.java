/*
 * Copyright 2013-2015 the original author or authors.
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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.client.http.HttpRequest;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

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
@AutoConfigureAfter(TraceAutoConfiguration.class)
public class TraceZuulAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public TracePreZuulFilter tracePreZuulFilter(Tracer tracer,
			SpanInjector<RequestContext> spanInjector, HttpTraceKeysInjector httpTraceKeysInjector) {
		return new TracePreZuulFilter(tracer, spanInjector, httpTraceKeysInjector);
	}

	@Bean
	public TraceRestClientRibbonCommandFactory traceRestClientRibbonCommandFactory(SpringClientFactory factory,
			Tracer tracer, SpanInjector<HttpRequest.Builder> spanInjector, HttpTraceKeysInjector httpTraceKeysInjector) {
		return new TraceRestClientRibbonCommandFactory(factory, tracer, spanInjector,
				httpTraceKeysInjector);
	}

	@Bean
	@ConditionalOnMissingBean
	public TracePostZuulFilter tracePostZuulFilter(Tracer tracer, TraceKeys traceKeys) {
		return new TracePostZuulFilter(tracer, traceKeys);
	}

	@Bean
	public SpanInjector<RequestContext> requestContextSpanInjector() {
		return new RequestContextInjector();
	}

	@Bean
	public SpanInjector<HttpRequest.Builder> requestBuilderContextSpanInjector() {
		return new RequestBuilderContextInjector();
	}

}
