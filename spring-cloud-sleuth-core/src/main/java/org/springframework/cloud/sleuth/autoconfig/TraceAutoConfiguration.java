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

package org.springframework.cloud.sleuth.autoconfig;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.exporter.SpanFilter;
import org.springframework.cloud.sleuth.api.noop.NoOpCurrentTraceContext;
import org.springframework.cloud.sleuth.api.noop.NoOpPropagator;
import org.springframework.cloud.sleuth.api.noop.NoOpSpanCustomizer;
import org.springframework.cloud.sleuth.api.noop.NoOpTracer;
import org.springframework.cloud.sleuth.api.propagation.Propagator;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable tracing via Spring Cloud Sleuth.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @author Tim Ysewyn
 * @since 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
@EnableConfigurationProperties({ SleuthSpanFilterProperties.class, SleuthBaggageProperties.class })
public class TraceAutoConfiguration {

	private static final Log log = LogFactory.getLog(TraceAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	Tracer defaultTracer() {
		if (log.isWarnEnabled()) {
			log.warn(
					"You have not provided a tracer implementation. A default, noop one will be set up. You will not see any spans get reported to external systems (e.g. Zipkin) nor will any context get propagated.");
		}
		return new NoOpTracer();
	}

	@Bean
	@ConditionalOnMissingBean
	SpanNamer defaultSpanNamer() {
		return new DefaultSpanNamer();
	}

	@Bean
	@ConditionalOnMissingBean
	Propagator defaultPropagator() {
		return new NoOpPropagator();
	}

	@Bean
	@ConditionalOnMissingBean
	CurrentTraceContext defaultCurrentTraceContext() {
		return new NoOpCurrentTraceContext();
	}

	@Bean
	@ConditionalOnMissingBean
	SpanCustomizer defaultSpanCustomizer() {
		return new NoOpSpanCustomizer();
	}

	@Bean
	@ConditionalOnProperty(value = "spring.sleuth.span-filter.enabled", matchIfMissing = true)
	SpanFilter spanIgnoringSpanExporter(SleuthSpanFilterProperties sleuthSpanFilterProperties) {
		return new SpanIgnoringSpanFilter(sleuthSpanFilterProperties);
	}

}
