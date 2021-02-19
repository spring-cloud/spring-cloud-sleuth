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

package org.springframework.cloud.sleuth.autoconfig.wavefront;

import brave.Tracer;
import brave.TracingCustomizer;
import brave.handler.SpanHandler;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.wavefront.WavefrontConfig;

import org.springframework.boot.actuate.autoconfigure.metrics.export.wavefront.WavefrontMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Wavefront tracing using Spring Cloud Sleuth.
 *
 * @author Adrian Cole
 * @author Stephane Nicoll
 * @since 3.1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ MeterRegistry.class, WavefrontConfig.class, WavefrontSender.class })
@ConditionalOnBean({ WavefrontSender.class, ApplicationTags.class })
@AutoConfigureAfter(WavefrontMetricsExportAutoConfiguration.class)
@AutoConfigureBefore(BraveAutoConfiguration.class)
@EnableConfigurationProperties(WavefrontProperties.class)
@ConditionalOnProperty(value = { "spring.sleuth.enabled", "spring.sleuth.wavefront.enabled" }, matchIfMissing = true)
public class WavefrontSleuthAutoConfiguration {

	@Bean
	@ConditionalOnBean({ MeterRegistry.class, WavefrontConfig.class, WavefrontSender.class, ApplicationTags.class })
	WavefrontSleuthSpanHandler wavefrontSleuthSpanHandler(MeterRegistry meterRegistry, WavefrontSender wavefrontSender,
			ApplicationTags applicationTags, WavefrontConfig wavefrontConfig, WavefrontProperties wavefrontProperties) {
		return new WavefrontSleuthSpanHandler(
				// https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/reporting/WavefrontSpanReporter.java#L54
				wavefrontProperties.getMaxQueueSize(), wavefrontSender, meterRegistry, wavefrontConfig.source(),
				applicationTags, wavefrontProperties.getRedMetricsCustomTagKeys());
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ Tracer.class, TracingCustomizer.class, SpanHandler.class })
	static class BraveCustomizerConfiguration {

		@Bean
		@ConditionalOnMissingBean(WavefrontTracingCustomizer.class)
		@ConditionalOnBean({ MeterRegistry.class, WavefrontConfig.class, WavefrontSender.class })
		WavefrontTracingCustomizer wavefrontTracingCustomizer(WavefrontSleuthSpanHandler spanHandler) {
			return new WavefrontTracingCustomizer(spanHandler);
		}

	}

}
