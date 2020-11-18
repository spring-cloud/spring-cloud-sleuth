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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.api.exporter.SpanFilter;
import org.springframework.cloud.sleuth.instrument.web.SleuthWebProperties;
import org.springframework.cloud.sleuth.instrument.web.TraceWebFluxConfiguration;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletConfiguration;
import org.springframework.cloud.sleuth.instrument.web.client.SleuthWebClientEnabled;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

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
@EnableConfigurationProperties({ SleuthSpanFilterProperties.class, SleuthBaggageProperties.class,
		SleuthTracerProperties.class })
@Import(SkipPatternConfiguration.class)
public class TraceConfiguration {

	@Bean
	@ConditionalOnMissingBean
	SpanNamer defaultSpanNamer() {
		return new DefaultSpanNamer();
	}

	@Bean
	@ConditionalOnProperty(value = "spring.sleuth.span-filter.enabled", matchIfMissing = true)
	SpanFilter spanIgnoringSpanExporter(SleuthSpanFilterProperties sleuthSpanFilterProperties) {
		return new SpanIgnoringSpanFilter(sleuthSpanFilterProperties);
	}

	@Configuration(proxyBeanMethods = false)
	@SleuthWebClientEnabled
	@Import({ SkipPatternConfiguration.class, TraceWebFluxConfiguration.class, TraceWebServletConfiguration.class,
			TraceWebFluxConfiguration.class })
	@EnableConfigurationProperties(SleuthWebProperties.class)
	static class TraceHttpConfiguration {

	}

}
