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

package org.springframework.cloud.sleuth.instrument.scheduling;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class TraceSchedulingAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
					TraceAutoConfiguration.class,
					TraceSchedulingAutoConfiguration.class));

	@Test
	void shoud_create_TraceSchedulingAspect() {
		this.contextRunner
				.run(context -> assertThat(context)
						.hasSingleBean(TraceSchedulingAspect.class)
						.hasSingleBean(AnnotationAwareAspectJAutoProxyCreator.class));
	}

	@Test
	void shoud_not_create_TraceSchedulingAspect_without_aspectJ() {
		this.contextRunner
				.withClassLoader(new FilteredClassLoader(ProceedingJoinPoint.class))
				.run(context -> assertThat(context)
						.doesNotHaveBean(TraceSchedulingAspect.class)
						.doesNotHaveBean(AnnotationAwareAspectJAutoProxyCreator.class));
	}

}
