/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.propagation;

import brave.handler.FinishedSpanHandler;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.SleuthProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Taras Danylchuk
 * @since 2.1.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
@AutoConfigureBefore(TraceAutoConfiguration.class)
public class SleuthTagPropagationAutoConfiguration {

	@Configuration
	@ConditionalOnProperty(value = "spring.sleuth.propagation.tag.enabled", matchIfMissing = true)
	@EnableConfigurationProperties(SleuthTagPropagationProperties.class)
	protected static class TagPropagationConfiguration {

		@Bean
		@ConditionalOnProperty(value = "spring.sleuth.propagation.tag.whitelisted-keys")
		public FinishedSpanHandler finishedSpanHandler(SleuthProperties sleuthProperties,
				SleuthTagPropagationProperties tagPropagationProperties) {
			return new TagPropagationFinishedSpanHandler(sleuthProperties,
					tagPropagationProperties);
		}

	}

}
