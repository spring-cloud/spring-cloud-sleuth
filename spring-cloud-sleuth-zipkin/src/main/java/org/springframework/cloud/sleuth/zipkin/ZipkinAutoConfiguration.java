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

package org.springframework.cloud.sleuth.zipkin;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.github.kristofa.brave.AnnotationSubmitterConfig;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.ClientTracerConfig;
import com.github.kristofa.brave.EndPointSubmitterConfig;
import com.github.kristofa.brave.FixedSampleRateTraceFilter;
import com.github.kristofa.brave.ServerSpanThreadBinderConfig;
import com.github.kristofa.brave.ServerTracerConfig;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.TraceFilter;
import com.github.kristofa.brave.TraceFilters;
import com.github.kristofa.brave.client.ClientRequestInterceptor;
import com.github.kristofa.brave.client.ClientResponseInterceptor;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;
import com.github.kristofa.brave.zipkin.ZipkinSpanCollector;
import com.google.common.base.Optional;

/**
 * @author Spencer Gibb
 */
@Configuration
@EnableConfigurationProperties
@ConditionalOnClass(ServerTracerConfig.class)
@ConditionalOnProperty(value = "spring.sleuth.zipkin.enabled", matchIfMissing = true)
@Import({ AnnotationSubmitterConfig.class, ClientTracerConfig.class,
	EndPointSubmitterConfig.class, ServerSpanThreadBinderConfig.class,
	ServerTracerConfig.class })
public class ZipkinAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(SpanCollector.class)
	public ZipkinSpanCollector spanCollector() {
		return new ZipkinSpanCollector(zipkinProperties().getHost(), zipkinProperties()
				.getPort());
	}

	@Bean
	public ZipkinProperties zipkinProperties() {
		return new ZipkinProperties();
	}

	@Bean
	public FixedSampleRateTraceFilter fixedSampleRateTraceFilter() {
		return new FixedSampleRateTraceFilter(zipkinProperties().getFixedSampleRate());
	}

	@Bean
	@ConditionalOnMissingBean
	public TraceFilters traceFilters(List<TraceFilter> traceFilters) {
		return new TraceFilters(traceFilters);
	}

	@Bean
	// @ConditionalOnProperty(value = "spring.sleuth.zipkin.braveTracer.enabled", havingValue = "false")
	public ZipkinSpanListener sleuthTracer(SpanCollector spanCollector) {
		return new ZipkinSpanListener(spanCollector);
	}

	@Configuration
	protected static class InterceptorConfig {

		@Autowired
		private ClientTracer clientTracer;

		@Autowired(required = false)
		private SpanNameFilter spanNameFilter;

		@Bean
		@ConditionalOnMissingBean
		public ClientRequestInterceptor clientRequestInterceptor() {
			return new ClientRequestInterceptor(this.clientTracer,
					Optional.fromNullable(this.spanNameFilter));
		}

		@Bean
		@ConditionalOnMissingBean
		public ClientResponseInterceptor clientResponseInterceptor() {
			return new ClientResponseInterceptor(this.clientTracer);
		}
	}
}
