/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.actuate;

import brave.handler.SpanHandler;

import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.Producible;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.autoconfig.brave.ConditionalOnBraveEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} for Sleuth actuator endpoint.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
@ConditionalOnAvailableEndpoint(endpoint = TracesScrapeEndpoint.class)
@AutoConfigureBefore(BraveAutoConfiguration.class)
@EnableConfigurationProperties(SleuthActuatorProperties.class)
@ConditionalOnClass(Producible.class)
public class TraceSleuthActuatorAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	BufferingSpanReporter sleuthBufferingSpanReporter(SleuthActuatorProperties sleuthActuatorProperties) {
		return new BufferingSpanReporter(sleuthActuatorProperties.getCapacity());
	}

	@Bean
	@ConditionalOnMissingBean
	TracesScrapeEndpoint sleuthTracesScrapeEndpoint(BufferingSpanReporter bufferingSpanReporter,
			FinishedSpanWriter finishedSpanWriter) {
		return new TracesScrapeEndpoint(bufferingSpanReporter, finishedSpanWriter);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(brave.Tracer.class)
	@ConditionalOnBraveEnabled
	static class BraveActuatorConfiguration {

		@Bean
		@ConditionalOnMissingBean
		FinishedSpanWriter sleuthBraveFinishedSpanWriter() {
			return new BraveFinishedSpanWriter();
		}

		/**
		 * We need to register at least one {@link SpanHandler} for sampling to hook in.
		 * If there are no span handlers all spans will be noop and no spans will get
		 * reported.
		 * @return a noop span handler
		 */
		@Bean
		SpanHandler sleuthBraveCustomSpanHandler() {
			return new SpanHandler() {

			};
		}

	}

}
