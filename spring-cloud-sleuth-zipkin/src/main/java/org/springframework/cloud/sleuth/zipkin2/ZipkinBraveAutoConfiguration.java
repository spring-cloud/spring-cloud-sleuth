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

package org.springframework.cloud.sleuth.zipkin2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import brave.Tag;
import brave.Tracer;
import brave.TracingCustomizer;
import brave.handler.SpanHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import zipkin2.Span;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestTemplate;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables reporting to Zipkin via HTTP.
 *
 * The {@link ZipkinRestTemplateCustomizer} allows you to customize the
 * {@link RestTemplate} that is used to send Spans to Zipkin. Its default implementation -
 * {@link DefaultZipkinRestTemplateCustomizer} adds the GZip compression.
 *
 * @author Spencer Gibb
 * @author Tim Ysewyn
 * @since 1.0.0
 * @see ZipkinRestTemplateCustomizer
 * @see DefaultZipkinRestTemplateCustomizer
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = { "spring.sleuth.enabled", "spring.zipkin.enabled" }, matchIfMissing = true)
@AutoConfigureBefore(TraceAutoConfiguration.class)
@ConditionalOnClass(Tracer.class)
@AutoConfigureAfter(ZipkinAutoConfiguration.class)
public class ZipkinBraveAutoConfiguration {

	/**
	 *
	 * Sort Zipkin Handlers last, so that redactions etc happen prior.
	 */
	static final Comparator<SpanHandler> SPAN_HANDLER_COMPARATOR = (o1, o2) -> {
		if (o1 instanceof ZipkinSpanHandler) {
			if (o2 instanceof ZipkinSpanHandler) {
				return 0;
			}
			return 1;
		}
		else if (o2 instanceof ZipkinSpanHandler) {
			return -1;
		}
		return 0;
	};

	private static final Log log = LogFactory.getLog(ZipkinBraveAutoConfiguration.class);

	/** Returns one handler for as many reporters as exist. */
	@Bean
	SpanHandler zipkinSpanHandler(@Nullable List<Reporter<Span>> spanReporters, @Nullable Tag<Throwable> errorTag) {
		if (spanReporters == null) {
			return SpanHandler.NOOP;
		}

		LinkedHashSet<Reporter<Span>> reporters = new LinkedHashSet<>(spanReporters);
		reporters.remove(Reporter.NOOP);
		if (spanReporters.isEmpty()) {
			return SpanHandler.NOOP;
		}

		Reporter<Span> spanReporter = reporters.size() == 1 ? reporters.iterator().next()
				: new CompositeSpanReporter(reporters.toArray(new Reporter[0]));

		ZipkinSpanHandler.Builder builder = ZipkinSpanHandler.newBuilder(spanReporter);
		if (errorTag != null) {
			builder.errorTag(errorTag);
		}
		return builder.build();
	}

	/** This ensures Zipkin reporters end up after redaction, etc. */
	@Bean
	TracingCustomizer reorderZipkinHandlersLast() {
		return builder -> {
			List<SpanHandler> configuredSpanHandlers = new ArrayList<>(builder.spanHandlers());
			configuredSpanHandlers.sort(SPAN_HANDLER_COMPARATOR);
			builder.clearSpanHandlers();
			for (SpanHandler spanHandler : configuredSpanHandlers) {
				builder.addSpanHandler(spanHandler);
			}
		};
	}

	// Zipkin conversion only happens once per mutable span
	static final class CompositeSpanReporter implements Reporter<Span> {

		final Reporter<Span>[] reporters;

		CompositeSpanReporter(Reporter<Span>[] reporters) {
			this.reporters = reporters;
		}

		@Override
		public void report(Span span) {
			for (Reporter<Span> reporter : reporters) {
				try {
					reporter.report(span);
				}
				catch (RuntimeException ex) {
					// TODO: message lifted from ListReporter: this is probably too much
					// for warn level
					log.warn("Exception occurred while trying to report the span " + span, ex);
				}
			}
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(reporters);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof CompositeSpanReporter)) {
				return false;
			}
			return Arrays.equals(((CompositeSpanReporter) obj).reporters, reporters);
		}

		@Override
		public String toString() {
			return Arrays.toString(reporters);
		}

	}

}
