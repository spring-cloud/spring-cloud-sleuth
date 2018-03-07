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
package org.springframework.cloud.sleuth.annotation;

import brave.Tracing;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that allows creating spans by means of a
 * {@link NewSpan} annotation. You can annotate classes or just methods.
 * You can also apply this annotation to an interface.
 *
 * @author Christian Schwerdtfeger
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@ConditionalOnBean(Tracing.class)
@ConditionalOnProperty(name = "spring.sleuth.annotation.enabled", matchIfMissing = true)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@EnableConfigurationProperties(SleuthAnnotationProperties.class)
public class SleuthAnnotationAutoConfiguration {
	
	@Bean
	@ConditionalOnMissingBean NewSpanParser newSpanParser() {
		return new DefaultNewSpanParser();
	}

	@Bean
	@ConditionalOnMissingBean TagValueExpressionResolver spelTagValueExpressionResolver() {
		return new SpelTagValueExpressionResolver();
	}

	@Bean
	@ConditionalOnMissingBean TagValueResolver noOpTagValueResolver() {
		return new NoOpTagValueResolver();
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE) SleuthAdvisorConfig sleuthAdvisorConfig() {
		return new SleuthAdvisorConfig();
	}

}
