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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Executor;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(
		classes = { AsyncAutoConfiguration.class,
				AsyncDisabledTests.ConfigureThreadPoolTaskScheduler.class },
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = "spring.sleuth.async.enabled=false")
public class AsyncDisabledTests {

	@Autowired
	@Qualifier("traceSenderThreadPool")
	Executor executor;

	@Test
	public void should_not_wrap_scheduler() {
		then(this.executor).isInstanceOf(ThreadPoolTaskScheduler.class);
	}

	@Configuration
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
