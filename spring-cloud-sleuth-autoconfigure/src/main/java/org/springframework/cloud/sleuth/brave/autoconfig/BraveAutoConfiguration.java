/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.autoconfig;

import java.util.Collections;
import java.util.List;

import brave.CurrentSpanCustomizer;
import brave.Tracer;
import brave.Tracing;
import brave.TracingCustomizer;
import brave.handler.SpanHandler;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContextCustomizer;
import brave.propagation.Propagation;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.annotation.SleuthAnnotationConfiguration;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.autoconfig.SleuthSpanFilterProperties;
import org.springframework.cloud.sleuth.autoconfig.SleuthTracerProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceConfiguration;
import org.springframework.cloud.sleuth.brave.LocalServiceName;
import org.springframework.cloud.sleuth.brave.OnBraveEnabled;
import org.springframework.cloud.sleuth.brave.SleuthProperties;
import org.springframework.cloud.sleuth.brave.bridge.BraveBridgeConfiguration;
import org.springframework.cloud.sleuth.brave.instrument.web.BraveHttpConfiguration;
import org.springframework.cloud.sleuth.brave.sampler.SamplerConfiguration;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable tracing via Spring Cloud Sleuth with Brave.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @author Tim Ysewyn
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@OnBraveEnabled
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
@ConditionalOnMissingBean(org.springframework.cloud.sleuth.api.Tracer.class)
@ConditionalOnClass({ Tracer.class, SleuthProperties.class })
@EnableConfigurationProperties({ SleuthProperties.class, SleuthSpanFilterProperties.class,
		SleuthBaggageProperties.class, SleuthTracerProperties.class, SleuthBaggageProperties.class })
@Import({ BraveBridgeConfiguration.class, BraveBaggageConfiguration.class, SamplerConfiguration.class,
		BraveHttpConfiguration.class, TraceConfiguration.class, SleuthAnnotationConfiguration.class })
public class BraveAutoConfiguration {

	/**
	 * Tracing bean name. Name of the bean matters for some instrumentations.
	 */
	public static final String TRACING_BEAN_NAME = "tracing";

	/**
	 * Tracer bean name. Name of the bean matters for some instrumentations.
	 */
	public static final String TRACER_BEAN_NAME = "tracer";

	/**
	 * Default value used for service name if none provided.
	 */
	private static final String DEFAULT_SERVICE_NAME = "default";

	@Bean(name = TRACING_BEAN_NAME)
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	Tracing tracing(@LocalServiceName String serviceName, Propagation.Factory factory,
			CurrentTraceContext currentTraceContext, Sampler sampler, SleuthProperties sleuthProperties,
			@Nullable List<SpanHandler> spanHandlers, @Nullable List<TracingCustomizer> tracingCustomizers) {
		Tracing.Builder builder = Tracing.newBuilder().sampler(sampler)
				.localServiceName(StringUtils.isEmpty(serviceName) ? DEFAULT_SERVICE_NAME : serviceName)
				.propagationFactory(factory).currentTraceContext(currentTraceContext)
				.traceId128Bit(sleuthProperties.isTraceId128()).supportsJoin(sleuthProperties.isSupportsJoin());
		if (spanHandlers != null) {
			for (SpanHandler spanHandlerFactory : spanHandlers) {
				builder.addSpanHandler(spanHandlerFactory);
			}
		}
		if (tracingCustomizers != null) {
			for (TracingCustomizer customizer : tracingCustomizers) {
				customizer.customize(builder);
			}
		}

		return builder.build();
	}

	@Bean(name = TRACER_BEAN_NAME)
	@ConditionalOnMissingBean
	Tracer tracer(Tracing tracing) {
		return tracing.tracer();
	}

	@Bean
	@ConditionalOnMissingBean
	SpanNamer sleuthSpanNamer() {
		return new DefaultSpanNamer();
	}

	@Bean
	CurrentTraceContext sleuthCurrentTraceContext(CurrentTraceContext.Builder builder,
			@Nullable List<CurrentTraceContext.ScopeDecorator> scopeDecorators,
			@Nullable List<CurrentTraceContextCustomizer> currentTraceContextCustomizers) {
		if (scopeDecorators == null) {
			scopeDecorators = Collections.emptyList();
		}
		if (currentTraceContextCustomizers == null) {
			currentTraceContextCustomizers = Collections.emptyList();
		}

		for (CurrentTraceContext.ScopeDecorator scopeDecorator : scopeDecorators) {
			builder.addScopeDecorator(scopeDecorator);
		}
		for (CurrentTraceContextCustomizer customizer : currentTraceContextCustomizers) {
			customizer.customize(builder);
		}
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	CurrentTraceContext.Builder sleuthCurrentTraceContextBuilder() {
		return ThreadLocalCurrentTraceContext.newBuilder();
	}

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	CurrentSpanCustomizer spanCustomizer(Tracing tracing) {
		return CurrentSpanCustomizer.create(tracing);
	}

}
