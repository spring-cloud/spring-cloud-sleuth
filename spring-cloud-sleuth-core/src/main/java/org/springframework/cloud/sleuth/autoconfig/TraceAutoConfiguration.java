/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import brave.CurrentSpanCustomizer;
import brave.ErrorParser;
import brave.Tracer;
import brave.Tracing;
import brave.TracingCustomizer;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.baggage.BaggagePropagationCustomizer;
import brave.handler.FinishedSpanHandler;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.CurrentTraceContextCustomizer;
import brave.propagation.Propagation;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import zipkin2.Span;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.metrics.micrometer.MicrometerReporterMetrics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.LocalServiceName;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

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
@EnableConfigurationProperties(SleuthProperties.class)
@AutoConfigureAfter(PropertyBasedBaggageConfiguration.class)
public class TraceAutoConfiguration {

	/**
	 * Tracer bean name. Name of the bean matters for some instrumentations.
	 */
	public static final String TRACER_BEAN_NAME = "tracer";

	/**
	 * Default value used for service name if none provided.
	 */
	public static final String DEFAULT_SERVICE_NAME = "default";

	@Autowired(required = false)
	List<FinishedSpanHandler> finishedSpanHandlers = new ArrayList<>();

	@Autowired(required = false)
	List<CurrentTraceContext.ScopeDecorator> scopeDecorators = new ArrayList<>();

	@Autowired(required = false)
	List<BaggagePropagationCustomizer> baggagePropagationCustomizers = new ArrayList<>();

	@Autowired(required = false)
	List<TracingCustomizer> tracingCustomizers = new ArrayList<>();

	@Autowired(required = false)
	List<CurrentTraceContextCustomizer> currentTraceContextCustomizers = new ArrayList<>();

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	Tracing tracing(@LocalServiceName String serviceName, Propagation.Factory factory,
			CurrentTraceContext currentTraceContext, Sampler sampler,
			ErrorParser errorParser, SleuthProperties sleuthProperties,
			@Nullable List<Reporter<zipkin2.Span>> spanReporters) {
		Tracing.Builder builder = Tracing.newBuilder().sampler(sampler)
				.errorParser(errorParser)
				.localServiceName(StringUtils.isEmpty(serviceName) ? DEFAULT_SERVICE_NAME
						: serviceName)
				.propagationFactory(factory).currentTraceContext(currentTraceContext)
				.spanReporter(new CompositeReporter(
						spanReporters != null ? spanReporters : Collections.emptyList()))
				.traceId128Bit(sleuthProperties.isTraceId128())
				.supportsJoin(sleuthProperties.isSupportsJoin());
		for (FinishedSpanHandler finishedSpanHandlerFactory : this.finishedSpanHandlers) {
			builder.addFinishedSpanHandler(finishedSpanHandlerFactory);
		}
		for (TracingCustomizer customizer : this.tracingCustomizers) {
			customizer.customize(builder);
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
	Sampler sleuthTraceSampler() {
		return Sampler.NEVER_SAMPLE;
	}

	@Bean
	@ConditionalOnMissingBean
	SpanNamer sleuthSpanNamer() {
		return new DefaultSpanNamer();
	}

	/**
	 * To override the underlying context format, override this bean and set the delegate
	 * to what you need. {@link BaggagePropagation.FactoryBuilder} will unwrap itself if
	 * no fields are configured.
	 */
	@Bean
	@ConditionalOnMissingBean
	BaggagePropagation.FactoryBuilder baggagePropagationFactoryBuilder() {
		return BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY);
	}

	@Bean
	@ConditionalOnMissingBean
	Propagation.Factory sleuthPropagation(
			BaggagePropagation.FactoryBuilder factoryBuilder,
			List<BaggagePropagationConfig> baggageConfig) {
		baggageConfig.forEach(factoryBuilder::add);
		for (BaggagePropagationCustomizer customizer : this.baggagePropagationCustomizers) {
			customizer.customize(factoryBuilder);
		}
		return factoryBuilder.build();
	}

	@Bean
	CurrentTraceContext sleuthCurrentTraceContext(CurrentTraceContext.Builder builder) {
		for (ScopeDecorator scopeDecorator : this.scopeDecorators) {
			builder.addScopeDecorator(scopeDecorator);
		}
		for (CurrentTraceContextCustomizer customizer : this.currentTraceContextCustomizers) {
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

	private static final class CompositeReporter implements Reporter<zipkin2.Span> {

		private static final Log log = LogFactory.getLog(CompositeReporter.class);

		private final Reporter<zipkin2.Span> spanReporter;

		private CompositeReporter(List<Reporter<Span>> spanReporters) {
			this.spanReporter = spanReporters.size() == 1 ? spanReporters.get(0)
					: new ListReporter(spanReporters);
		}

		@Override
		public void report(Span span) {
			this.spanReporter.report(span);
		}

		@Override
		public String toString() {
			return "CompositeReporter{ spanReporters=" + this.spanReporter + '}';
		}

		private static final class ListReporter implements Reporter<zipkin2.Span> {

			private final List<Reporter<Span>> spanReporters;

			private ListReporter(List<Reporter<Span>> spanReporters) {
				this.spanReporters = spanReporters;
			}

			@Override
			public void report(Span span) {
				for (Reporter<zipkin2.Span> spanReporter : this.spanReporters) {
					try {
						spanReporter.report(span);
					}
					catch (Exception ex) {
						log.warn("Exception occurred while trying to report the span "
								+ span, ex);
					}
				}
			}

			@Override
			public String toString() {
				return "ListReporter{" + "spanReporters=" + this.spanReporters + '}';
			}

		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass("io.micrometer.core.instrument.MeterRegistry")
	static class TraceMetricsInMemoryConfiguration {

		@Bean
		@ConditionalOnMissingBean
		ReporterMetrics sleuthReporterMetrics() {
			return new InMemoryReporterMetrics();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(MeterRegistry.class)
	static class TraceMetricsMicrometerConfiguration {

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnMissingBean(ReporterMetrics.class)
		static class NoReporterMetricsBeanConfiguration {

			@Bean
			@ConditionalOnBean(MeterRegistry.class)
			ReporterMetrics sleuthMicrometerReporterMetrics(MeterRegistry meterRegistry) {
				return MicrometerReporterMetrics.create(meterRegistry);
			}

			@Bean
			@ConditionalOnMissingBean(MeterRegistry.class)
			ReporterMetrics sleuthReporterMetrics() {
				return new InMemoryReporterMetrics();
			}

		}

	}

}
