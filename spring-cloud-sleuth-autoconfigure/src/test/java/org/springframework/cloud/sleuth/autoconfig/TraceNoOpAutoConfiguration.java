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

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.api.propagation.Propagator;
import org.springframework.cloud.sleuth.brave.autoconfig.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.client.SleuthWebClientEnabled;
import org.springframework.cloud.sleuth.otel.autoconfig.OtelAutoConfiguration;
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
@ConditionalOnProperty("spring.sleuth.noop.enabled")
@AutoConfigureBefore({ BraveAutoConfiguration.class, OtelAutoConfiguration.class })
@Import({ SkipPatternConfiguration.class, TraceConfiguration.class })
public class TraceNoOpAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Tracer defaultTracer() {
		return new NoOpTracer();
	}

	@Bean
	Propagator defaultPropagator() {
		return new NoOpPropagator();
	}

	@Bean
	CurrentTraceContext defaultCurrentTraceContext() {
		return new NoOpCurrentTraceContext();
	}

	@Bean
	SpanCustomizer defaultSpanCustomizer() {
		return new NoOpSpanCustomizer();
	}

	@Configuration(proxyBeanMethods = false)
	@SleuthWebClientEnabled
	static class TraceHttpConfiguration {

		@Bean
		HttpClientHandler defaultHttpClientHandler() {
			return new NoOpHttpClientHandler();
		}

		@Bean
		HttpServerHandler defaultHttpServerHandler() {
			return new NoOpHttpServerHandler();
		}

	}

}
