/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.quartz;

import brave.Tracing;
import org.quartz.Scheduler;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables Quartz span information propagation.
 *
 * @author Branden Cash
 * @since 2.2.0
 * @deprecated This type should have never been public and will be hidden or removed in
 * 3.0
 */
@Deprecated
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean({ Tracing.class, Scheduler.class })
@AutoConfigureAfter({ TraceAutoConfiguration.class, QuartzAutoConfiguration.class })
@ConditionalOnProperty(value = "spring.sleuth.quartz.enabled", matchIfMissing = true)
public class TraceQuartzAutoConfiguration implements InitializingBean {

	private Scheduler scheduler;

	private Tracing tracing;

	public TraceQuartzAutoConfiguration(Scheduler scheduler, Tracing tracing) {
		this.scheduler = scheduler;
		this.tracing = tracing;
	}

	@Autowired
	BeanFactory beanFactory;

	@Bean
	public TracingJobListener tracingJobListener() {
		return new TracingJobListener(tracing);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		TracingJobListener tracingJobListener = beanFactory
				.getBean(TracingJobListener.class);
		scheduler.getListenerManager().addTriggerListener(tracingJobListener);
		scheduler.getListenerManager().addJobListener(tracingJobListener);
	}

}
