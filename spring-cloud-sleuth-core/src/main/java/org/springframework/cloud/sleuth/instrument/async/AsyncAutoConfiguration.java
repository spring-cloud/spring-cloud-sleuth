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

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} for asynchronous communication.
 *
 * @author Jesus Alonso
 * @since 2.1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(Tracer.class)
@ConditionalOnProperty(value = "spring.sleuth.async.enabled", matchIfMissing = true)
@EnableConfigurationProperties(SleuthAsyncProperties.class)
class AsyncAutoConfiguration {

	@Bean
	SleuthContextListener traceContextClosedListener() {
		return new SleuthContextListener();
	}

}
