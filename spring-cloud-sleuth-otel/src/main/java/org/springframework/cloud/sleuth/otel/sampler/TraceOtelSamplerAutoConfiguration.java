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

package org.springframework.cloud.sleuth.otel.sampler;

import io.opentelemetry.sdk.extensions.trace.jaeger.sampler.JaegerRemoteSampler;
import io.opentelemetry.trace.Tracer;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.sleuth.otel.autoconfig.TraceOtelAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable sampler configuration via Spring Cloud Sleuth and
 * OpenTelemetry SDK.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(Tracer.class)
@AutoConfigureBefore(TraceOtelAutoConfiguration.class)
public class TraceOtelSamplerAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(JaegerRemoteSampler.class)
	static class JaegerRemoteSamplerConfiguration {

		@Bean
		JaegerRemoteSampler.Builder otelJaegerRemoteSampler() {
			return JaegerRemoteSampler.newBuilder();
			// TODO: a BPP around an existing sampler?
			// .setChannel()
			// .setServiceName()
			// .withInitialSampler();
		}

	}

}
