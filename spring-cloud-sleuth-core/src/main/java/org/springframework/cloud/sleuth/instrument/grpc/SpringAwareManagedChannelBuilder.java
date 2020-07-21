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

package org.springframework.cloud.sleuth.instrument.grpc;

import java.util.List;
import java.util.Optional;

import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;

/**
 * This is a Spring-aware managed channel builder that wraps the static entry points of
 * gRPC's ManagedChannelBuilder to allow the configuration of the builder to be influenced
 * by the Spring Context. All GrpcManagedChannelBuilderCustomizer instances included in
 * the application context will have the opportunity to customize the builder.
 *
 * NOTE: There is nothing "Sleuth-specific" about this, however, there is currently not a
 * good spring abstraction for client-side gRPC. Ideally, this could be moved up into
 * grpc-spring-boot-starter or a new project could be created
 * "spring-grpc"/"spring-cloud-grpc"?
 *
 * @author Tyler Van Gorder
 */
// TODO: research why we need to continue to maintain this given current libraries
public class SpringAwareManagedChannelBuilder {

	private List<GrpcManagedChannelBuilderCustomizer> customizers;

	public SpringAwareManagedChannelBuilder(
			Optional<List<GrpcManagedChannelBuilderCustomizer>> customizers) {
		this.customizers = customizers.orElse(null);
	}

	public ManagedChannelBuilder<?> forAddress(String name, int port) {
		ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(name, port);
		customize(builder);
		return builder;
	}

	public ManagedChannelBuilder<?> forTarget(String target) {
		ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target);
		customize(builder);
		return builder;
	}

	public ManagedChannelBuilder<?> inProcessChannelBuilder(String serverName) {
		ManagedChannelBuilder<?> builder = InProcessChannelBuilder.forName(serverName);
		customize(builder);
		return builder;
	}

	private void customize(ManagedChannelBuilder<?> builder) {
		if (this.customizers != null) {
			for (GrpcManagedChannelBuilderCustomizer customizer : this.customizers) {
				customizer.customize(builder);
			}
		}
	}

}
