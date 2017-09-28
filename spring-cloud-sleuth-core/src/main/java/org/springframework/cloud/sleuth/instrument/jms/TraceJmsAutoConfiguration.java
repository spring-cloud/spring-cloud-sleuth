/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.jms;

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
 * Registers beans related to jms.
 *
 * @author Stefan Zeller
 * @since 1.2.5
 *
 * @see TraceJmsListenerAspect
 */
@Configuration
@EnableAspectJAutoProxy
@ConditionalOnProperty(value = "spring.sleuth.jms.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@EnableConfigurationProperties(SleuthJmsListenerProperties.class)
public class TraceJmsAutoConfiguration {

	@ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
	@Bean
	public TraceJmsListenerAspect traceJmsListenerAspect(Tracer tracer, TraceKeys traceKeys, SleuthJmsListenerProperties sleuthJmsListenerProperties) {
		return new TraceJmsListenerAspect(tracer, traceKeys, Pattern.compile(sleuthJmsListenerProperties.getSkipPattern()));
	}
}
