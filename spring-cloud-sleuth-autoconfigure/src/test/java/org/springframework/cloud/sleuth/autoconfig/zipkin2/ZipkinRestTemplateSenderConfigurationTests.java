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

package org.springframework.cloud.sleuth.autoconfig.zipkin2;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.zipkin2.ZipkinProperties;
import org.springframework.cloud.sleuth.zipkin2.ZipkinRestTemplateCustomizer;
import org.springframework.cloud.sleuth.zipkin2.ZipkinRestTemplateProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ZipkinRestTemplateSenderConfigurationTests {

	@Test
	void should_override_the_default_rest_template() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class,
				ZipkinRestTemplateSenderConfiguration.class, ZipkinProperties.class);

		runner.run(context -> {
			Config config = context.getBean(Config.class);
			assertThat(config.customizerCalled).isTrue();
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		boolean customizerCalled;

		@Bean
		ZipkinRestTemplateProvider myZipkinRestTemplateProvider() {
			return MyRestTemplate::new;
		}

		@Bean
		ZipkinRestTemplateCustomizer myZipkinRestTemplateCustomizer() {
			return new ZipkinRestTemplateCustomizer() {
				@Override
				public RestTemplate customizeTemplate(RestTemplate restTemplate) {
					customizerCalled = true;
					BDDAssertions.then(restTemplate).isInstanceOf(MyRestTemplate.class);
					return restTemplate;
				}
			};
		}

	}

	static class MyRestTemplate extends RestTemplate {

	}

}
