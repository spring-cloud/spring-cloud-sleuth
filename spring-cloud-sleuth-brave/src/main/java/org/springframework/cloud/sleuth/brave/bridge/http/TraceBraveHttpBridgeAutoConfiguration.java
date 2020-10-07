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

package org.springframework.cloud.sleuth.brave.bridge.http;

import brave.http.HttpTracing;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.autoconfig.http.TraceHttpAutoConfiguration;
import org.springframework.cloud.sleuth.brave.bridge.TraceBraveBridgeAutoConfiguation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
@ConditionalOnBean({ Tracer.class, HttpTracing.class })
@AutoConfigureBefore(TraceHttpAutoConfiguration.class)
@AutoConfigureAfter(TraceBraveBridgeAutoConfiguation.class)
public class TraceBraveHttpBridgeAutoConfiguration {

	@Bean
	HttpClientHandler braveHttpClientHandler(HttpTracing httpTracing) {
		return new BraveHttpClientHandler(brave.http.HttpClientHandler.create(httpTracing));
	}

	@Bean
	HttpServerHandler braveHttpServerHandler(HttpTracing httpTracing) {
		return new BraveHttpServerHandler(brave.http.HttpServerHandler.create(httpTracing));
	}

}
