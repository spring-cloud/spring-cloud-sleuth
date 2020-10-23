/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.internal;

import brave.propagation.CurrentTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

public class LazyBeanTests {

	AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@AfterEach
	public void close() {
		context.close();
	}

	@Test
	public void should_work_with_basic_type() {
		context.register(BasicConfig.class);
		context.refresh();

		LazyBean<CurrentTraceContext> provider = LazyBean.create(context, CurrentTraceContext.class);

		then(provider.get()).isNotNull();
	}

	@Test
	public void should_return_null_when_no_basic_type() {
		context.refresh();

		LazyBean<CurrentTraceContext> provider = LazyBean.create(context, CurrentTraceContext.class);

		then(provider.get()).isNull();
	}

	@Configuration(proxyBeanMethods = false)
	static class BasicConfig {

		@Bean
		CurrentTraceContext currentTraceContext() {
			return CurrentTraceContext.Default.create();
		}

	}

}
