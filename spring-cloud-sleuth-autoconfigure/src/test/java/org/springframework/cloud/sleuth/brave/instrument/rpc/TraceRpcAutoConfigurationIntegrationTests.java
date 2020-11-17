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

package org.springframework.cloud.sleuth.brave.instrument.rpc;

import brave.handler.SpanHandler;
import brave.rpc.RpcRequest;
import brave.rpc.RpcRuleSampler;
import brave.sampler.Matcher;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static brave.sampler.Matchers.and;
import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(classes = TraceRpcAutoConfigurationIntegrationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.sleuth.tracer.mode=BRAVE")
public class TraceRpcAutoConfigurationIntegrationTests {

	@Autowired
	@RpcServerSampler
	SamplerFunction<RpcRequest> sampler;

	@Test
	public void should_inject_rpc_sampler() {
		then(this.sampler).isNotNull();
	}

	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class })
	@Configuration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		// tag::custom_rpc_server_sampler[]
		@Bean(name = RpcServerSampler.NAME)
		SamplerFunction<RpcRequest> myRpcSampler() {
			Matcher<RpcRequest> userAuth = and(serviceEquals("users.UserService"), methodEquals("GetUserToken"));
			return RpcRuleSampler.newBuilder().putRule(serviceEquals("grpc.health.v1.Health"), Sampler.NEVER_SAMPLE)
					.putRule(userAuth, RateLimitingSampler.create(100)).build();
		}
		// end::custom_rpc_server_sampler[]

	}

}
