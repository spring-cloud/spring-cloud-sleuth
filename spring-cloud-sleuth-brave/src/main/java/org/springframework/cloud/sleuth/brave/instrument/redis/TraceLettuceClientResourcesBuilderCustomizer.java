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

package org.springframework.cloud.sleuth.brave.instrument.redis;

import brave.Tracing;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.tracing.BraveTracing;

/**
 * {@link ClientResourcesBuilderCustomizer} for wrapping Lettuce components in a tracing
 * representation.
 *
 * @author Marcin Grzejszczak
 * @author Thomas Vitale
 * @since 3.0.4
 */
public class TraceLettuceClientResourcesBuilderCustomizer implements ClientResourcesBuilderCustomizer {

	private final BraveTracing tracing;

	public TraceLettuceClientResourcesBuilderCustomizer(Tracing tracing, String serviceName) {
		this.tracing = BraveTracing.builder().tracing(tracing).excludeCommandArgsFromSpanTags().serviceName(serviceName)
				.build();
	}

	@Override
	public void customize(ClientResources.Builder builder) {
		builder.tracing(this.tracing);
	}

}
