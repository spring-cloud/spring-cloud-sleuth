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

package org.springframework.cloud.sleuth.brave.bridge;

import java.util.List;

import brave.Tracing;
import brave.handler.SpanHandler;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.exporter.SpanFilter;
import org.springframework.cloud.sleuth.api.propagation.Propagator;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.brave.propagation.PropagationFactorySupplier;
import org.springframework.cloud.sleuth.brave.propagation.SleuthPropagationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SleuthPropagationProperties.class)
public class BraveBridgeConfiguration {

	@Bean
	org.springframework.cloud.sleuth.api.Tracer braveTracer(brave.Tracer tracer,
			BraveBaggageManager braveBaggageManager) {
		return new BraveTracer(tracer, braveBaggageManager);
	}

	@Bean
	org.springframework.cloud.sleuth.api.CurrentTraceContext braveCurrentTraceContext(
			brave.propagation.CurrentTraceContext currentTraceContext) {
		return new BraveCurrentTraceContext(currentTraceContext);
	}

	@Bean
	SpanCustomizer braveSpanCustomizer(brave.SpanCustomizer spanCustomizer) {
		return new BraveSpanCustomizer(spanCustomizer);
	}

	@Bean
	Propagator bravePropagator(Tracing tracing) {
		return new BravePropagator(tracing);
	}

	@Bean
	@ConditionalOnMissingBean
	BraveBaggageManager braveBaggageManager() {
		return new BraveBaggageManager();
	}

	@Bean
	@ConditionalOnMissingBean
	PropagationFactorySupplier compositePropagationFactorySupplier(BeanFactory beanFactory,
			SleuthBaggageProperties baggageProperties, SleuthPropagationProperties properties) {
		return new CompositePropagationFactorySupplier(beanFactory, baggageProperties, properties);
	}

	// Name is important for sampling conditions
	@Bean(name = "traceCompositeSpanHandler")
	SpanHandler compositeSpanHandler(@Nullable List<SpanFilter> exporters) {
		return new CompositeSpanHandler(exporters);
	}

}
