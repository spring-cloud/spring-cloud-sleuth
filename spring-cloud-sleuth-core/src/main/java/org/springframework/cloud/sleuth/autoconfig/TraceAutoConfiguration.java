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

package org.springframework.cloud.sleuth.autoconfig;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.sampler.DefaultStringToUuidConverter;
import org.springframework.cloud.sleuth.sampler.IsTracingSampler;
import org.springframework.cloud.sleuth.sampler.StringToUuidConverter;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.IdGenerator;
import org.springframework.util.JdkIdGenerator;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(value="spring.sleuth.enabled", matchIfMissing=true)
public class TraceAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public IdGenerator traceIdGenerator() {
		return new JdkIdGenerator();
	}

	@Bean
	@ConditionalOnMissingBean
	public Sampler<Void> defaultTraceSampler() {
		return new IsTracingSampler();
	}

	@Bean
	@ConditionalOnMissingBean
	public StringToUuidConverter stringToUuidConverter() {
		return new DefaultStringToUuidConverter();
	}

	@Bean
	@ConditionalOnMissingBean
	public DefaultTraceManager traceManager(Sampler<Void> sampler, IdGenerator idGenerator,
			ApplicationEventPublisher publisher) {
		return new DefaultTraceManager(sampler, idGenerator, publisher);
	}
}
