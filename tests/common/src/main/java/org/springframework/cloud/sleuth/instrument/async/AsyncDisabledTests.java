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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@ContextConfiguration(classes = AsyncDisabledTests.ConfigureThreadPoolTaskScheduler.class)
@TestPropertySource(properties = "spring.sleuth.scheduled.enabled=false")
public abstract class AsyncDisabledTests {

	@Autowired
	CurrentTraceContext currentTraceContext;

	@Autowired
	@Qualifier("traceSenderThreadPool")
	Executor executor;

	/**
	 * We can't check the type of the executor because sleuth will proxy it. Instead, we
	 * check for behaviour.
	 */
	@Test
	public void should_not_wrap_scheduler() throws InterruptedException {
		BlockingQueue<Boolean> spans = new LinkedBlockingQueue<>();
		this.executor.execute(() -> spans.add(this.currentTraceContext.context() != null));
		BDDAssertions.then(spans.take()).isFalse();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EnableAsync
	static class ConfigureThreadPoolTaskScheduler {

		@Bean
		@ConditionalOnMissingBean(name = "traceSenderThreadPool")
		public ThreadPoolTaskScheduler traceSenderThreadPool() {
			return new ThreadPoolTaskScheduler();
		}

	}

}
