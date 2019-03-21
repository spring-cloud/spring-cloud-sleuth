/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.scheduling;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.regex.Pattern;

/**
 * Registers beans related to task scheduling.
 *
 * @author Michal Chmielarz, 4financeIT
 * @author Spencer Gibb
 * @since 1.0.0
 *
 * @see TraceSchedulingAspect
 */
@Configuration
@EnableAspectJAutoProxy
@ConditionalOnProperty(value = "spring.sleuth.scheduled.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@EnableConfigurationProperties(SleuthSchedulingProperties.class)
public class TraceSchedulingAutoConfiguration {

	@ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
	@Bean
	public TraceSchedulingAspect traceSchedulingAspect(Tracer tracer, TraceKeys traceKeys,
			SleuthSchedulingProperties sleuthSchedulingProperties) {
		return new TraceSchedulingAspect(tracer, traceKeys, Pattern.compile(sleuthSchedulingProperties.getSkipPattern()));
	}
}
