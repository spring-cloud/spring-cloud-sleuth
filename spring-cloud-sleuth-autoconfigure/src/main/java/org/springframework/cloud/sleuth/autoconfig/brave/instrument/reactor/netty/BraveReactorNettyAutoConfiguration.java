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

package org.springframework.cloud.sleuth.autoconfig.brave.instrument.reactor.netty;

import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import reactor.netty.NettyPipeline;
import reactor.netty.http.brave.ReactorNettyHttpTracing;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.brave.instrument.reactor.netty.TracingChannelInboundHandler;
import org.springframework.cloud.sleuth.brave.instrument.reactor.netty.TracingChannelOutboundHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable additional tracing with Reactor Netty.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.9
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ HttpTracing.class, ReactorNettyHttpTracing.class })
@AutoConfigureAfter(BraveAutoConfiguration.class)
public class BraveReactorNettyAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty("spring.sleuth.reactor.netty.debug.enabled")
	@ConditionalOnBean(HttpTracing.class)
	static class DebugReactorNettyConfiguration {

		static final String INBOUND_NAME = NettyPipeline.LEFT + "customTracingChannelInboundHandler";

		static final String OUTBOUND_NAME = NettyPipeline.RIGHT + "customTracingChannelOutboundHandler";

		@Bean
		public NettyServerCustomizer tracingNettyServerCustomizer(HttpTracing httpTracing) {
			return server -> ReactorNettyHttpTracing.create(httpTracing).decorateHttpServer(server)
					.doOnChannelInit((obs, ch, addr) -> {
						CurrentTraceContext currentTraceContext = httpTracing.tracing().currentTraceContext();

						String oldNameInboundHandler = NettyPipeline.LEFT + "tracingChannelInboundHandler";
						ch.pipeline().remove(oldNameInboundHandler);
						ch.pipeline().addFirst(INBOUND_NAME, new TracingChannelInboundHandler(currentTraceContext));

						String oldNameOutboundHandler = NettyPipeline.RIGHT + "tracingChannelOutboundHandler";
						ch.pipeline().replace(oldNameOutboundHandler, OUTBOUND_NAME,
								new TracingChannelOutboundHandler(currentTraceContext));
					});
		}

		@Bean
		public HttpClientCustomizer tracingHttpClientCustomizer(HttpTracing httpTracing) {
			return client -> client.doOnChannelInit((obs, ch, addr) -> {
				CurrentTraceContext currentTraceContext = httpTracing.tracing().currentTraceContext();
				ch.pipeline().addFirst(INBOUND_NAME, new TracingChannelInboundHandler(currentTraceContext));
				ch.pipeline().addBefore(NettyPipeline.ReactiveBridge, OUTBOUND_NAME,
						new TracingChannelOutboundHandler(currentTraceContext));
			});
		}

	}

}
