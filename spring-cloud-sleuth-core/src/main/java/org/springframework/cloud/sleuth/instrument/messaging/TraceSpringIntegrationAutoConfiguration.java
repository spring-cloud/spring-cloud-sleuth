/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.ChannelInterceptorAware;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.InterceptableChannel;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that registers a Sleuth version of the
 * {@link org.springframework.messaging.support.ChannelInterceptor}.
 *
 * @author Spencer Gibb
 * @since 1.0.0
 *
 * @see TraceChannelInterceptor
 */
@Configuration
@ConditionalOnClass(GlobalChannelInterceptor.class)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter({ TraceAutoConfiguration.class,
		TraceSpanMessagingAutoConfiguration.class })
@ConditionalOnProperty(value = "spring.sleuth.integration.enabled", matchIfMissing = true)
@EnableConfigurationProperties(TraceKeys.class)
public class TraceSpringIntegrationAutoConfiguration {

	@Bean
	@GlobalChannelInterceptor(patterns = "${spring.sleuth.integration.patterns:*}")
	public TraceChannelInterceptor traceChannelInterceptor(BeanFactory beanFactory) {
		return new IntegrationTraceChannelInterceptor(beanFactory);
	}

	@Bean TraceDestinationResolverAspect traceDestinationResolverAspect(TraceChannelInterceptor interceptor) {
		return new TraceDestinationResolverAspect(interceptor);
	}
}

@Aspect
class TraceDestinationResolverAspect {

	private final TraceChannelInterceptor interceptor;

	TraceDestinationResolverAspect(TraceChannelInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@Pointcut("execution(public * org.springframework.messaging.core.DestinationResolver.resolveDestination(..))")
	private void anyDestinationResolver() { } // NOSONAR

	@Around("anyDestinationResolver()")
	@SuppressWarnings("unchecked")
	public Object addInterceptor(ProceedingJoinPoint pjp) throws Throwable {
		Object destination = pjp.proceed();
		if (destination instanceof ChannelInterceptorAware) {
			ChannelInterceptorAware interceptorAware = (ChannelInterceptorAware) destination;
			if (!hasTracedChannelInterceptor(interceptorAware.getChannelInterceptors())) {
				interceptorAware.addInterceptor(this.interceptor);
			}
		} else if (destination instanceof InterceptableChannel) {
			InterceptableChannel interceptorAware = (InterceptableChannel) destination;
			if (!hasTracedChannelInterceptor(interceptorAware.getInterceptors())) {
				interceptorAware.addInterceptor(this.interceptor);
			}
		}
		return destination;
	}

	private boolean hasTracedChannelInterceptor(List<ChannelInterceptor> interceptors) {
		for (ChannelInterceptor channelInterceptor : interceptors) {
			if (channelInterceptor instanceof TraceChannelInterceptor) {
				return true;
			}
		}
		return false;
	}
}