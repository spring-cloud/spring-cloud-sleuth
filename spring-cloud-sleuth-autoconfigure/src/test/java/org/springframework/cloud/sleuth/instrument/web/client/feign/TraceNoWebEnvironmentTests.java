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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class TraceNoWebEnvironmentTests {

	// issue #32
	@Test
	public void should_work_when_using_web_client_without_the_web_environment() {
		SpringApplication springApplication = new SpringApplication(Config.class);
		springApplication.setWebApplicationType(WebApplicationType.NONE);

		try (ConfigurableApplicationContext context = springApplication.run("--spring.sleuth.noop.enabled=true")) {
			Config.SomeFeignClient client = context.getBean(Config.SomeFeignClient.class);
			client.createSomeTestRequest();
		}
		catch (Exception e) {
			then(e.getCause().getClass()).isNotEqualTo(NoSuchBeanDefinitionException.class);
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EnableFeignClients(clients = Config.SomeFeignClient.class)
	public static class Config {

		@FeignClient(name = "google", url = "https://www.google.com/")
		public interface SomeFeignClient {

			@RequestMapping(value = "/", method = RequestMethod.GET)
			String createSomeTestRequest();

		}

	}

}
