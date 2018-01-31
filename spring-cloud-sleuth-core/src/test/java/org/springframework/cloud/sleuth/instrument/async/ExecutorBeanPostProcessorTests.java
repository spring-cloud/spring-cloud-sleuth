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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class ExecutorBeanPostProcessorTests {

	@Mock BeanFactory beanFactory;

	@Test
	public void should_create_a_cglib_proxy_by_default() throws Exception {
		Object o = new ExecutorBeanPostProcessor(this.beanFactory)
				.postProcessAfterInitialization(new Foo(), "foo");

		then(o).isInstanceOf(Foo.class);
		then(ClassUtils.isCglibProxy(o)).isTrue();
	}

	class Foo implements Executor {
		@Override public void execute(Runnable command) {

		}
	}

	@Test
	public void should_create_jdk_proxy_when_cglib_fails_to_be_done() throws Exception {
		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();

		Object o = new ExecutorBeanPostProcessor(this.beanFactory)
				.postProcessAfterInitialization(service, "foo");

		then(o).isInstanceOf(ScheduledExecutorService.class);
		then(ClassUtils.isCglibProxy(o)).isFalse();
		service.shutdown();
	}

	@Test
	public void should_throw_exception_when_it_is_not_possible_to_create_any_proxy() throws Exception {
		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		ExecutorBeanPostProcessor bpp = new ExecutorBeanPostProcessor(this.beanFactory) {
			@Override Object createProxy(Object bean, boolean cglibProxy,
					Executor executor) {
				throw new AopConfigException("foo");
			}
		};

		thenThrownBy(() -> bpp.postProcessAfterInitialization(service, "foo"))
				.isInstanceOf(AopConfigException.class)
				.hasMessage("foo");
		service.shutdown();
	}

	@Test
	public void should_create_a_cglib_proxy_by_default_for_ThreadPoolTaskExecutor() throws Exception {
		Object o = new ExecutorBeanPostProcessor(this.beanFactory)
				.postProcessAfterInitialization(new FooThreadPoolTaskExecutor(), "foo");

		then(o).isInstanceOf(FooThreadPoolTaskExecutor.class);
		then(ClassUtils.isCglibProxy(o)).isTrue();
	}

	class FooThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {
	}

	@Test
	public void should_throw_exception_when_it_is_not_possible_to_create_any_proxyfor_ThreadPoolTaskExecutor() throws Exception {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		ExecutorBeanPostProcessor bpp = new ExecutorBeanPostProcessor(this.beanFactory) {
			@Override Object createThreadPoolTaskExecutorProxy(Object bean, boolean cglibProxy,
					ThreadPoolTaskExecutor executor) {
				throw new AopConfigException("foo");
			}
		};

		thenThrownBy(() -> bpp.postProcessAfterInitialization(taskExecutor, "foo"))
				.isInstanceOf(AopConfigException.class)
				.hasMessage("foo");
	}

}