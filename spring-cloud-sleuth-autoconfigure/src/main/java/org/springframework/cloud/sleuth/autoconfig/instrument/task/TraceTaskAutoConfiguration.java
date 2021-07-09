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

package org.springframework.cloud.sleuth.autoconfig.instrument.task;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.task.TraceTaskExecutionListener;
import org.springframework.cloud.task.listener.TaskExecutionListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that registers instrumentation for Spring Cloud Task.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(TaskExecutionListener.class)
@ConditionalOnProperty(value = "spring.sleuth.task.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(BraveAutoConfiguration.class)
public class TraceTaskAutoConfiguration {

	@Bean
	TraceTaskExecutionListener traceTaskExecutionListener(Tracer tracer,
			@Value("${spring.application.name:default}") String appName) {
		return new TraceTaskExecutionListener(tracer, appName);
	}

	@Bean
	static TraceCommandLineRunnerBeanPostProcessor traceCommandLineRunnerBeanPostProcessor(BeanFactory beanFactory) {
		return new TraceCommandLineRunnerBeanPostProcessor(beanFactory);
	}

	@Bean
	static TraceApplicationRunnerBeanPostProcessor traceApplicationRunnerBeanPostProcessor(BeanFactory beanFactory) {
		return new TraceApplicationRunnerBeanPostProcessor(beanFactory);
	}

}
