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

package org.springframework.cloud.sleuth.brave.instrument.grpc;

import java.util.List;
import java.util.Optional;

import brave.Tracing;
import brave.grpc.GrpcTracing;
import brave.rpc.RpcTracing;
import io.grpc.ServerInterceptor;
import org.lognet.springboot.grpc.GRpcGlobalInterceptor;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.brave.instrument.rpc.TraceRpcAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables span information propagation when using GRPC.
 *
 * This configuration is only enabled when both grpc-spring-boot-starter and
 * brave-instrumentation-grpc are on the classpath.
 *
 * @author Tyler Van Gorder
 */
@ConditionalOnClass({ GrpcTracing.class, GRpcGlobalInterceptor.class })
@ConditionalOnProperty(value = "spring.sleuth.grpc.enabled", matchIfMissing = true)
@ConditionalOnBean({ Tracing.class, RpcTracing.class })
@AutoConfigureAfter(TraceRpcAutoConfiguration.class)
class TraceGrpcAutoConfiguration {

	@Bean
	public GrpcTracing grpcTracing(RpcTracing rpcTracing) {
		return GrpcTracing.create(rpcTracing);
	}

	// Register a global interceptor for both the server
	@Bean
	@GRpcGlobalInterceptor
	ServerInterceptor grpcServerBraveInterceptor(GrpcTracing grpcTracing) {
		return grpcTracing.newServerInterceptor();
	}

	// This is wrapper around gRPC's managed channel builder that is spring-aware
	@Bean
	@ConditionalOnMissingBean(SpringAwareManagedChannelBuilder.class)
	public SpringAwareManagedChannelBuilder managedChannelBuilder(
			Optional<List<GrpcManagedChannelBuilderCustomizer>> customizers) {
		return new SpringAwareManagedChannelBuilder(customizers);
	}

	@Bean
	GrpcManagedChannelBuilderCustomizer tracingManagedChannelBuilderCustomizer(GrpcTracing grpcTracing) {
		return new TracingManagedChannelBuilderCustomizer(grpcTracing);
	}

}
