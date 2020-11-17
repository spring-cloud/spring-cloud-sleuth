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

package org.springframework.cloud.sleuth.instrument.web.client;

import javax.annotation.PostConstruct;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GH846Tests.App.class, webEnvironment = WebEnvironment.NONE,
		properties = "spring.sleuth.noop.enabled=true")
public class GH846Tests {

	@Autowired
	private MyBean myBean;

	@Test
	public void doit() throws Exception {
		int count = this.myBean.listAndCount();
		assertThat(this.myBean.getCountAtPostConstruct())
				.as("Change detected in RestTemplate interceptor *after* @PostConstruct").isEqualTo(count);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class })
	static class App {

		@Bean
		public RestTemplate myRestTemplate() {
			return new RestTemplate();
		}

		@Bean
		public MyBean myBean() {
			return new MyBean();
		}

	}

	static class MyBean {

		@Autowired
		private RestTemplate restTemplate;

		/**
		 * Number of interceptors registered in the RestTemplate during @PostConstruct.
		 */
		private int countAtPostConstruct;

		@PostConstruct
		public void init() {
			this.countAtPostConstruct = listAndCount();
		}

		public int listAndCount() {
			for (ClientHttpRequestInterceptor interceptor : this.restTemplate.getInterceptors()) {
				System.out.println(interceptor);
			}
			return this.restTemplate.getInterceptors().size();
		}

		public int getCountAtPostConstruct() {
			return this.countAtPostConstruct;
		}

	}

}
