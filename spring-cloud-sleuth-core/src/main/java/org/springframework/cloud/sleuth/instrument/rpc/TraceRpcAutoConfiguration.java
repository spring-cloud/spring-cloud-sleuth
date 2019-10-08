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

import java.util.ArrayList;
import java.util.List;

import brave.Tracing;
import brave.rpc.RpcRequest;
import brave.rpc.RpcTracing;
import brave.rpc.RpcTracingCustomizer;
import brave.sampler.SamplerFunction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} related to RPC based communication.
 *
 * @since 2.2.0
 */
@Configuration
@ConditionalOnProperty(name = "spring.sleuth.rpc.enabled", havingValue = "true",
		matchIfMissing = true)
@ConditionalOnBean(Tracing.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
public class TraceRpcAutoConfiguration {

	@Autowired(required = false)
	List<RpcTracingCustomizer> rpcTracingCustomizers = new ArrayList<>();

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	RpcTracing rpcTracing(Tracing tracing,
			@Nullable @RpcClientSampler SamplerFunction<RpcRequest> clientSampler,
			@Nullable @RpcServerSampler SamplerFunction<RpcRequest> serverSampler) {

		RpcTracing.Builder builder = RpcTracing.newBuilder(tracing);
		if (clientSampler != null) {
			builder.clientSampler(clientSampler);
		}
		if (serverSampler != null) {
			builder.serverSampler(serverSampler);
		}
		for (RpcTracingCustomizer customizer : this.rpcTracingCustomizers) {
			customizer.customize(builder);
		}
		return builder.build();
	}

}
