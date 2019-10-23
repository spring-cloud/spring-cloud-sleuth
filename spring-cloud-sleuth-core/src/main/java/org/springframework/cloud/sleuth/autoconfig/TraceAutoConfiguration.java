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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import brave.CurrentSpanCustomizer;
import brave.ErrorParser;
import brave.Tracer;
import brave.Tracing;
import brave.TracingCustomizer;
import brave.handler.FinishedSpanHandler;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContextCustomizer;
import brave.propagation.ExtraFieldCustomizer;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import zipkin2.Span;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.ReporterMetrics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.LocalServiceName;
import org.springframework.cloud.sleuth.SpanAdjuster;
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
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
@EnableConfigurationProperties(SleuthProperties.class)
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
	List<SpanAdjuster> spanAdjusters = new ArrayList<>();

	@Autowired(required = false)
	List<FinishedSpanHandler> finishedSpanHandlers = new ArrayList<>();

	@Autowired(required = false)
	List<CurrentTraceContext.ScopeDecorator> scopeDecorators = new ArrayList<>();

	@Autowired(required = false)
	ExtraFieldPropagation.FactoryBuilder extraFieldPropagationFactoryBuilder;

	@Autowired(required = false)
	List<TracingCustomizer> tracingCustomizers = new ArrayList<>();

	@Autowired(required = false)
	List<CurrentTraceContextCustomizer> currentTraceContextCustomizers = new ArrayList<>();

	@Autowired(required = false)
	List<ExtraFieldCustomizer> extraFieldCustomizers = new ArrayList<>();

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	Tracing tracing(@LocalServiceName String serviceName, Propagation.Factory factory,
			CurrentTraceContext currentTraceContext, Sampler sampler, ErrorParser errorParser,
			SleuthProperties sleuthProperties, @Nullable List<Reporter<zipkin2.Span>> spanReporters) {
		Tracing.Builder builder = Tracing.newBuilder().sampler(sampler).errorParser(errorParser)
				.localServiceName(StringUtils.isEmpty(serviceName) ? DEFAULT_SERVICE_NAME : serviceName)
				.propagationFactory(factory).currentTraceContext(currentTraceContext)
				.spanReporter(new CompositeReporter(this.spanAdjusters,
						spanReporters != null ? spanReporters : Collections.emptyList()))
				.traceId128Bit(sleuthProperties.isTraceId128()).supportsJoin(sleuthProperties.isSupportsJoin());
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

	@Bean
	@ConditionalOnMissingBean
	Propagation.Factory sleuthPropagation(SleuthProperties sleuthProperties) {
		if (sleuthProperties.getBaggageKeys().isEmpty() && sleuthProperties.getPropagationKeys().isEmpty()
				&& extraFieldCustomizers.isEmpty()) {
			return B3Propagation.FACTORY;
		}
		ExtraFieldPropagation.FactoryBuilder factoryBuilder;
		if (this.extraFieldPropagationFactoryBuilder != null) {
			factoryBuilder = this.extraFieldPropagationFactoryBuilder;
		}
		else {
			factoryBuilder = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY);
		}
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
		if (!sleuthProperties.getLocalKeys().isEmpty()) {
			for (String key : sleuthProperties.getLocalKeys()) {
				factoryBuilder = factoryBuilder.addRedactedField(key);
			}
		}
		for (ExtraFieldCustomizer customizer : this.extraFieldCustomizers) {
			customizer.customize(factoryBuilder);
		}
		return factoryBuilder.build();
	}

	@Bean
	CurrentTraceContext sleuthCurrentTraceContext(CurrentTraceContext.Builder builder) {
		for (CurrentTraceContext.ScopeDecorator scopeDecorator : this.scopeDecorators) {
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

		private final List<SpanAdjuster> spanAdjusters;

		private final Reporter<zipkin2.Span> spanReporter;

		private CompositeReporter(List<SpanAdjuster> spanAdjusters, List<Reporter<Span>> spanReporters) {
			this.spanAdjusters = spanAdjusters;
			this.spanReporter = spanReporters.size() == 1 ? spanReporters.get(0) : new ListReporter(spanReporters);
		}

		@Override
		public void report(Span span) {
			Span spanToAdjust = span;
			for (SpanAdjuster spanAdjuster : this.spanAdjusters) {
				spanToAdjust = spanAdjuster.adjust(spanToAdjust);
			}
			this.spanReporter.report(spanToAdjust);
		}

		@Override
		public String toString() {
			return "CompositeReporter{" + "spanAdjusters=" + this.spanAdjusters + ", spanReporters=" + this.spanReporter
					+ '}';
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
						log.warn("Exception occurred while trying to report the span " + span, ex);
					}
				}
			}

			@Override
			public String toString() {
				return "ListReporter{" + "spanReporters=" + this.spanReporters + '}';
			}

		}

	}

	@Configuration
	@ConditionalOnMissingBean(MeterRegistry.class)
	static class TraceMetricsNoOpConfiguration {

		@Bean
		@ConditionalOnMissingBean
		ReporterMetrics sleuthReporterMetrics() {
			return new InMemoryReporterMetrics();
		}

	}

	@Configuration
	@ConditionalOnBean(MeterRegistry.class)
	static class TraceMicrometerMetricsConfiguration {

		@Bean
		@ConditionalOnMissingBean
		ReporterMetrics sleuthMicrometerReporterMetrics(MeterRegistry meterRegistry) {
			return new MicrometerReporterMetrics(meterRegistry);
		}

		static final class MicrometerReporterMetrics implements ReporterMetrics {

			private final Counter messages;

			private final Counter messageBytes;

			private final Counter spans;

			private final Counter spanBytes;

			private final Counter spansDropped;

			private final AtomicLong spanPending;

			private final AtomicLong spanBytesPending;

			private final Map<Class<? extends Throwable>, Counter> messagesDropped = new ConcurrentHashMap<>();

			private final MeterRegistry meterRegistry;

			MicrometerReporterMetrics(MeterRegistry meterRegistry) {
				this.messages = meterRegistry.counter("sleuth.messages");
				this.messageBytes = meterRegistry.counter("sleuth.messageBytes");
				this.spans = meterRegistry.counter("sleuth.spans");
				this.spanBytes = meterRegistry.counter("sleuth.spanBytes");
				this.spansDropped = meterRegistry.counter("sleuth.spansDropped");
				this.spanPending = meterRegistry.gauge("sleuth.spanPending", new AtomicLong());
				this.spanBytesPending = meterRegistry.gauge("sleuth.spanBytesPending", new AtomicLong());
				this.meterRegistry = meterRegistry;
			}

			@Override
			public void incrementMessages() {
				this.messages.increment();
			}

			@Override
			public void incrementMessagesDropped(Throwable cause) {
				Counter counter = this.messagesDropped.get(cause.getClass());
				if (counter == null) {
					counter = this.meterRegistry.counter("sleuth.messagesDropped." + cause.getClass().getSimpleName());
					this.messagesDropped.put(cause.getClass(), counter);
				}
				counter.increment();
			}

			@Override
			public void incrementSpans(int quantity) {
				this.spans.increment();
			}

			@Override
			public void incrementSpanBytes(int quantity) {
				this.spanBytes.increment(quantity);
			}

			@Override
			public void incrementMessageBytes(int quantity) {
				this.messageBytes.increment(quantity);
			}

			@Override
			public void incrementSpansDropped(int quantity) {
				this.spansDropped.increment(quantity);
			}

			@Override
			public void updateQueuedSpans(int update) {
				this.spanPending.set(update);
			}

			@Override
			public void updateQueuedBytes(int update) {
				this.spanBytesPending.set(update);
			}

		}

	}

}
