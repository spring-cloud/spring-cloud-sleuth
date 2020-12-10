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

package org.springframework.cloud.sleuth.instrument.redis;

import java.net.SocketAddress;

import brave.Tracing;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.tracing.BraveTracing;
import io.lettuce.core.tracing.TraceContext;
import io.lettuce.core.tracing.TraceContextProvider;
import io.lettuce.core.tracing.Tracer;
import io.lettuce.core.tracing.TracerProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.internal.ContextUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables Redis span information propagation.
 *
 * @author Chao Chang
 * @since 2.2.0
 * @deprecated This type should have never been public and will be hidden or removed in
 * 3.0
 */
@Deprecated
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.redis.enabled", matchIfMissing = true)
@ConditionalOnBean({ Tracing.class, ClientResources.class })
@AutoConfigureAfter({ TraceAutoConfiguration.class })
@EnableConfigurationProperties(TraceRedisProperties.class)
public class TraceRedisAutoConfiguration {

	@Bean
	static TraceLettuceClientResourcesBeanPostProcessor traceLettuceClientResourcesBeanPostProcessor(
			BeanFactory beanFactory) {
		return new TraceLettuceClientResourcesBeanPostProcessor(beanFactory);
	}

}

class TraceLettuceClientResourcesBeanPostProcessor implements BeanPostProcessor {

	private static final Log log = LogFactory
			.getLog(TraceLettuceClientResourcesBeanPostProcessor.class);

	private final BeanFactory beanFactory;

	TraceLettuceClientResourcesBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof ClientResources) {
			ClientResources cr = (ClientResources) bean;
			if (!cr.tracing().isEnabled()) {
				if (log.isDebugEnabled()) {
					log.debug(
							"Lettuce ClientResources bean is auto-configured to enable tracing.");
				}
				return cr.mutate().tracing(new LazyTracing(this.beanFactory)).build();
			}
			else if (log.isDebugEnabled()) {
				log.debug(
						"Lettuce ClientResources bean is skipped for auto-configuration because tracing was already enabled.");
			}
		}
		return bean;
	}

}

class LazyTracing implements io.lettuce.core.tracing.Tracing {

	private final BeanFactory beanFactory;

	private final io.lettuce.core.tracing.Tracing noOpTracing = NoOpTracing.INSTANCE;

	private BraveTracing braveTracing;

	LazyTracing(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public TracerProvider getTracerProvider() {
		if (ContextUtil.isContextUnusable(this.beanFactory)) {
			return this.noOpTracing.getTracerProvider();
		}
		return braveTracing().getTracerProvider();
	}

	@Override
	public TraceContextProvider initialTraceContextProvider() {
		if (ContextUtil.isContextUnusable(this.beanFactory)) {
			return this.noOpTracing.initialTraceContextProvider();
		}
		return braveTracing().initialTraceContextProvider();
	}

	@Override
	public boolean isEnabled() {
		if (ContextUtil.isContextUnusable(this.beanFactory)) {
			return this.noOpTracing.isEnabled();
		}
		return braveTracing().isEnabled();
	}

	@Override
	public boolean includeCommandArgsInSpanTags() {
		if (ContextUtil.isContextUnusable(this.beanFactory)) {
			return this.noOpTracing.includeCommandArgsInSpanTags();
		}
		return braveTracing().includeCommandArgsInSpanTags();
	}

	@Override
	public Endpoint createEndpoint(SocketAddress socketAddress) {
		if (ContextUtil.isContextUnusable(this.beanFactory)) {
			return this.noOpTracing.createEndpoint(socketAddress);
		}
		return braveTracing().createEndpoint(socketAddress);
	}

	private BraveTracing braveTracing() {
		if (this.braveTracing == null) {
			this.braveTracing = BraveTracing.builder()
					.tracing(this.beanFactory.getBean(Tracing.class))
					.excludeCommandArgsFromSpanTags().serviceName(this.beanFactory
							.getBean(TraceRedisProperties.class).getRemoteServiceName())
					.build();
		}
		return this.braveTracing;
	}

}

enum NoOpTracing
		implements io.lettuce.core.tracing.Tracing, TraceContextProvider, TracerProvider {

	INSTANCE;

	private final Endpoint NOOP_ENDPOINT = new Endpoint() {
	};

	@Override
	public TraceContext getTraceContext() {
		return TraceContext.EMPTY;
	}

	@Override
	public Tracer getTracer() {
		return NoOpTracer.INSTANCE;
	}

	@Override
	public TracerProvider getTracerProvider() {
		return this;
	}

	@Override
	public TraceContextProvider initialTraceContextProvider() {
		return this;
	}

	@Override
	public boolean isEnabled() {
		return false;
	}

	@Override
	public boolean includeCommandArgsInSpanTags() {
		return false;
	}

	@Override
	public Endpoint createEndpoint(SocketAddress socketAddress) {
		return NOOP_ENDPOINT;
	}

	static class NoOpTracer extends Tracer {

		static final Tracer INSTANCE = new NoOpTracer();

		@Override
		public Span nextSpan(TraceContext traceContext) {
			return NoOpSpan.INSTANCE;
		}

		@Override
		public Span nextSpan() {
			return NoOpSpan.INSTANCE;
		}

	}

	public static class NoOpSpan extends Tracer.Span {

		static final NoOpSpan INSTANCE = new NoOpSpan();

		@Override
		public Tracer.Span start() {
			return this;
		}

		@Override
		public Tracer.Span name(String name) {
			return this;
		}

		@Override
		public Tracer.Span annotate(String value) {
			return this;
		}

		@Override
		public Tracer.Span tag(String key, String value) {
			return this;
		}

		@Override
		public Tracer.Span error(Throwable throwable) {
			return this;
		}

		@Override
		public Tracer.Span remoteEndpoint(
				io.lettuce.core.tracing.Tracing.Endpoint endpoint) {
			return this;
		}

		@Override
		public void finish() {
		}

	}

}
