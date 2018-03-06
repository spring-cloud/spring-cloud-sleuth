/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web;

import brave.Tracing;
import brave.http.HttpTracing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * related to HTTP based communication.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnBean(Tracing.class)
@ConditionalOnProperty(name = "spring.sleuth.http.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(TraceWebAutoConfiguration.class)
public class TraceHttpAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	HttpTracing httpTracing(
			@Value("${spring.sleuth.http.legacy.enabled:false}") boolean legacyEnabled,
			Tracing tracing,
			TraceKeys traceKeys,
			ErrorParser errorParser,
			SkipPatternProvider provider
	) {
		if (legacyEnabled) {
			return HttpTracing.newBuilder(tracing)
					.clientParser(new SleuthHttpClientParser(traceKeys))
					.serverParser(new SleuthHttpServerParser(traceKeys, errorParser))
					.serverSampler(new SleuthHttpSampler(provider))
					.build();
		}
		return HttpTracing
				.newBuilder(tracing)
				.serverSampler(new SleuthHttpSampler(provider))
				.build();
	}
}
