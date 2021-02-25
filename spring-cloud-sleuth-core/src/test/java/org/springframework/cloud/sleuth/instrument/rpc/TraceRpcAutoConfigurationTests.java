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

package org.springframework.cloud.sleuth.instrument.rpc;

import brave.rpc.RpcRequest;
import brave.rpc.RpcTracing;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import org.junit.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

public class TraceRpcAutoConfigurationTests {

	@Test
	public void defaultsToBraveRpcClientSampler() {
		contextRunner().run((context) -> {
			SamplerFunction<RpcRequest> clientSampler = context.getBean(RpcTracing.class)
					.clientSampler();

			then(clientSampler).isSameAs(SamplerFunctions.deferDecision());
		});
	}

	@Test
	public void configuresUserProvidedRpcClientSampler() {
		contextRunner().withUserConfiguration(RpcClientSamplerConfig.class)
				.run((context) -> {
					SamplerFunction<RpcRequest> clientSampler = context
							.getBean(RpcTracing.class).clientSampler();

					then(clientSampler).isSameAs(RpcClientSamplerConfig.INSTANCE);
				});
	}

	@Test
	public void defaultsToBraveRpcServerSampler() {
		contextRunner().run((context) -> {
			SamplerFunction<RpcRequest> serverSampler = context.getBean(RpcTracing.class)
					.serverSampler();

			then(serverSampler).isSameAs(SamplerFunctions.deferDecision());
		});
	}

	@Test
	public void configuresUserProvidedRpcServerSampler() {
		contextRunner().withUserConfiguration(RpcServerSamplerConfig.class)
				.run((context) -> {
					SamplerFunction<RpcRequest> serverSampler = context
							.getBean(RpcTracing.class).serverSampler();

					then(serverSampler).isSameAs(RpcServerSamplerConfig.INSTANCE);
				});
	}

	private ApplicationContextRunner contextRunner(String... propertyValues) {
		return new ApplicationContextRunner().withPropertyValues(propertyValues)
				.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class,
						TraceRpcAutoConfiguration.class,
						TraceRpcAutoConfiguration.class));
	}

}

@Configuration
class RpcClientSamplerConfig {

	static final SamplerFunction<RpcRequest> INSTANCE = request -> null;

	@Bean(RpcClientSampler.NAME)
	SamplerFunction<RpcRequest> sleuthRpcClientSampler() {
		return INSTANCE;
	}

}

@Configuration
class RpcServerSamplerConfig {

	static final SamplerFunction<RpcRequest> INSTANCE = request -> null;

	@Bean(RpcServerSampler.NAME)
	SamplerFunction<RpcRequest> sleuthRpcServerSampler() {
		return INSTANCE;
	}

}
