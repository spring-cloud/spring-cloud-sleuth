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

package org.springframework.cloud.sleuth.autoconfig;

import java.util.ArrayList;
import java.util.List;

import brave.CurrentSpanCustomizer;
import brave.ErrorParser;
import brave.Tracer;
import brave.Tracing;
import brave.context.log4j2.ThreadContextCurrentTraceContext;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.sampler.Sampler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.SpanAdjuster;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * to enable tracing via Spring Cloud Sleuth.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnProperty(value="spring.sleuth.enabled", matchIfMissing=true)
@EnableConfigurationProperties(SleuthProperties.class)
public class TraceAutoConfiguration {

	public static final String TRACER_BEAN_NAME = "tracer";

	@Autowired(required = false) List<SpanAdjuster> spanAdjusters = new ArrayList<>();

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	Tracing tracing(@Value("${spring.zipkin.service.name:${spring.application.name:default}}") String serviceName,
			Propagation.Factory factory,
			CurrentTraceContext currentTraceContext,
			Reporter<zipkin2.Span> reporter,
			Sampler sampler,
			ErrorParser errorParser,
			SleuthProperties sleuthProperties
	) {
		return Tracing.newBuilder()
				.sampler(sampler)
				.errorParser(errorParser)
				.localServiceName(serviceName)
				.propagationFactory(factory)
				.currentTraceContext(currentTraceContext)
				.spanReporter(adjustedReporter(reporter))
				.traceId128Bit(sleuthProperties.isTraceId128())
				.supportsJoin(sleuthProperties.isSupportsJoin())
				.build();
	}

	private Reporter<zipkin2.Span> adjustedReporter(Reporter<zipkin2.Span> delegate) {
		return span -> {
			Span spanToAdjust = span;
			for (SpanAdjuster spanAdjuster : this.spanAdjusters) {
				spanToAdjust = spanAdjuster.adjust(spanToAdjust);
			}
			delegate.report(spanToAdjust);
		};
	}

	@Bean(name = TRACER_BEAN_NAME)
	@ConditionalOnMissingBean
	Tracer tracer(Tracing tracing) {
		return tracing.tracer();
	}

	@Bean
	@ConditionalOnMissingBean
	Sampler sleuthTraceSampler() {
		return Sampler.NEVER_SAMPLE;
	}

	@Bean
	@ConditionalOnMissingBean SpanNamer sleuthSpanNamer() {
		return new DefaultSpanNamer();
	}

	@Bean
	@ConditionalOnMissingBean
	Propagation.Factory sleuthPropagation(SleuthProperties sleuthProperties) {
		if (sleuthProperties.getBaggageKeys().isEmpty() && sleuthProperties.getPropagationKeys().isEmpty()) {
			return B3Propagation.FACTORY;
		}
		ExtraFieldPropagation.FactoryBuilder factoryBuilder = ExtraFieldPropagation
				.newFactoryBuilder(B3Propagation.FACTORY);
		if (!sleuthProperties.getBaggageKeys().isEmpty()) {
			factoryBuilder = factoryBuilder
					// for HTTP
					.addPrefixedFields("baggage-", sleuthProperties.getBaggageKeys())
					// for messaging
					.addPrefixedFields("baggage_", sleuthProperties.getBaggageKeys());
		}
		if (!sleuthProperties.getPropagationKeys().isEmpty()) {
			for (String key : sleuthProperties.getPropagationKeys()) {
				factoryBuilder = factoryBuilder.addField(key);
			}
		}
		return factoryBuilder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	CurrentTraceContext sleuthCurrentTraceContext() {
		return ThreadContextCurrentTraceContext.create();
	}

	@Bean
	@ConditionalOnMissingBean
	Reporter<zipkin2.Span> noOpSpanReporter() {
		return Reporter.NOOP;
	}

	@Bean
	@ConditionalOnMissingBean
	ErrorParser errorParser() {
		return new ErrorParser();
	}

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	CurrentSpanCustomizer spanCustomizer(Tracing tracing) {
		return CurrentSpanCustomizer.create(tracing);
	}
}
