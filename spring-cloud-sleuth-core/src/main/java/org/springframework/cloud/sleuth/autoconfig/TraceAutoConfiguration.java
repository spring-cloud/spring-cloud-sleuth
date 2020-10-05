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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.api.SamplerFunction;
import org.springframework.cloud.sleuth.api.SamplingFlags;
import org.springframework.cloud.sleuth.api.ScopedSpan;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.Tracer;
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
public class TraceAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Tracer defaultTracer() {
		return new Tracer() {
			@Override
			public Span newTrace() {
				return null;
			}

			@Override
			public Span joinSpan(TraceContext context) {
				return null;
			}

			@Override
			public Span newChild(TraceContext parent) {
				return null;
			}

			@Override
			public Span nextSpan(TraceContext extracted) {
				return null;
			}

			@Override
			public Span nextSpan(SamplingFlags extracted) {
				return null;
			}

			@Override
			public Span toSpan(TraceContext context) {
				return null;
			}

			@Override
			public SpanInScope withSpanInScope(Span span) {
				return null;
			}

			@Override
			public SpanCustomizer currentSpanCustomizer() {
				return null;
			}

			@Override
			public Span currentSpan() {
				return null;
			}

			@Override
			public Span nextSpan() {
				return null;
			}

			@Override
			public ScopedSpan startScopedSpan(String name) {
				return null;
			}

			@Override
			public <T> ScopedSpan startScopedSpan(String name, SamplerFunction<T> samplerFunction, T arg) {
				return null;
			}

			@Override
			public <T> Span nextSpan(SamplerFunction<T> samplerFunction, T arg) {
				return null;
			}

			@Override
			public <T> Span nextSpanWithParent(SamplerFunction<T> samplerFunction, T arg, TraceContext parent) {
				return null;
			}

			@Override
			public ScopedSpan startScopedSpanWithParent(String name, TraceContext parent) {
				return null;
			}
		};
	}

	@Bean
	@ConditionalOnMissingBean
	SpanNamer sleuthSpanNamer() {
		return new DefaultSpanNamer();
	}

}
