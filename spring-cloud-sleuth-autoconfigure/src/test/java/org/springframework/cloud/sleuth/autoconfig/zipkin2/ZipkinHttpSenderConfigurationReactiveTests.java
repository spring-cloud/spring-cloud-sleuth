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

import org.junit.jupiter.api.Test;
import zipkin2.reporter.Sender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.cloud.sleuth.zipkin2.WebClientSender;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
class ZipkinHttpSenderConfigurationReactiveTests {

	@Test
	void should_work_when_using_web_client_without_the_web_environment() {
		SpringApplication springApplication = new SpringApplication(Config.class);
		springApplication.setWebApplicationType(WebApplicationType.REACTIVE);

		try (ConfigurableApplicationContext context = springApplication.run("--spring.sleuth.noop.enabled=true")) {
			then(context.getBean(Sender.class)).isInstanceOf(WebClientSender.class);
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class, R2dbcAutoConfiguration.class,
			R2dbcDataAutoConfiguration.class, RedisAutoConfiguration.class, CassandraAutoConfiguration.class })
	public static class Config {

	}

}
