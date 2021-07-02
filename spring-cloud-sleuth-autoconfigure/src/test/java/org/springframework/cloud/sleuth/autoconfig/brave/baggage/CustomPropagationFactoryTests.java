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

package org.springframework.cloud.sleuth.autoconfig.brave.baggage;

import java.util.Collections;
import java.util.List;

import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class CustomPropagationFactoryTests {

	@Test
	void should_fail_to_start_the_context_when_propagation_type_custom_and_no_custom_propagation_provided() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
				.withPropertyValues("spring.sleuth.propagation.type=custom")
				.run(context -> BDDAssertions.then(context).hasFailed());
	}

	@Test
	void should_start_the_context_when_propagation_type_custom_and_no_custom_propagation_provided() {
		new ApplicationContextRunner().withUserConfiguration(CustomConfig.class)
				.withPropertyValues("spring.sleuth.propagation.type=custom").run(context -> BDDAssertions.then(context)
						.hasNotFailed().getBean(CustomConfig.CustomPropagation.class));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class })
	static class Config {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class })
	static class CustomConfig {

		@Bean
		CustomPropagation customPropagation() {
			return new CustomPropagation();
		}

		static class CustomPropagation extends Propagation.Factory implements Propagation<String> {

			@Override
			public List<String> keys() {
				return Collections.emptyList();
			}

			@Override
			public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
				return (traceContext, request) -> {
				};
			}

			@Override
			public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
				return request -> TraceContextOrSamplingFlags.EMPTY;
			}

			@Override
			public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
				return StringPropagationAdapter.create(this, keyFactory);
			}

		}

	}

}
