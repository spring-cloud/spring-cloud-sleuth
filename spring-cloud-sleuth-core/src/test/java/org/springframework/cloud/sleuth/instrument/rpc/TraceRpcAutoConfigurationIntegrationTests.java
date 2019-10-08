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

package org.springframework.cloud.sleuth.instrument.rpc;

import brave.rpc.RpcRequest;
import brave.rpc.RpcRuleSampler;
import brave.sampler.Matcher;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static brave.sampler.Matchers.and;
import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TraceRpcAutoConfigurationIntegrationTests.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TraceRpcAutoConfigurationIntegrationTests {

	@Autowired
	@RpcServerSampler
	SamplerFunction<RpcRequest> sampler;

	@Test
	public void should_inject_rpc_sampler() {
		then(this.sampler).isNotNull();
	}

	@EnableAutoConfiguration
	@Configuration
	public static class Config {

		@Bean
		ArrayListSpanReporter reporter() {
			return new ArrayListSpanReporter();
		}

		// tag::custom_rpc_server_sampler[]
		@Bean(name = RpcServerSampler.NAME)
		SamplerFunction<RpcRequest> myRpcSampler() {
			Matcher<RpcRequest> userAuth = and(serviceEquals("users.UserService"),
					methodEquals("GetUserToken"));
			return RpcRuleSampler.newBuilder()
					.putRule(serviceEquals("grpc.health.v1.Health"), Sampler.NEVER_SAMPLE)
					.putRule(userAuth, RateLimitingSampler.create(100)).build();
		}
		// end::custom_rpc_server_sampler[]

	}

}
