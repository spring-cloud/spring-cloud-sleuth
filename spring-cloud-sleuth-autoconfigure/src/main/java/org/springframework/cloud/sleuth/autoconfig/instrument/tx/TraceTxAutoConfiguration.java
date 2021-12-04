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

package org.springframework.cloud.sleuth.autoconfig.instrument.tx;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.tx.TracePlatformTransactionManagerAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that registers instrumentation for Spring TX.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.tx.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(BraveAutoConfiguration.class)
public class TraceTxAutoConfiguration {

	@Bean
	@ConditionalOnClass(name = "org.springframework.transaction.PlatformTransactionManager")
	TracePlatformTransactionManagerAspect tracePlatformTransactionManagerAspect(Tracer tracer,
			BeanFactory beanFactory) {
		return new TracePlatformTransactionManagerAspect(tracer, beanFactory);
	}

	@Bean
	@ConditionalOnClass(
			name = { "org.springframework.transaction.ReactiveTransactionManager", "reactor.core.publisher.Mono" })
	static TraceReactiveTransactionManagerBeanPostProcessor traceReactiveTransactionManagerBeanPostProcessor(
			BeanFactory beanFactory) {
		return new TraceReactiveTransactionManagerBeanPostProcessor(beanFactory);
	}

}
