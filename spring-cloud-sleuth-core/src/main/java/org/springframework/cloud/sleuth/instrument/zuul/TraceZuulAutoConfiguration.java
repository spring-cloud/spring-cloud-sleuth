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
package org.springframework.cloud.sleuth.instrument.zuul;

import brave.http.HttpTracing;
import com.netflix.zuul.ZuulFilter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommand;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables span information propagation when using Zuul.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.zuul.enabled", matchIfMissing = true)
@ConditionalOnWebApplication
@ConditionalOnClass(ZuulFilter.class)
@ConditionalOnBean(HttpTracing.class)
@AutoConfigureAfter(TraceWebServletAutoConfiguration.class)
public class TraceZuulAutoConfiguration {

	@Bean
	TracePostZuulFilter tracePostZuulFilter(HttpTracing httpTracing) {
		return new TracePostZuulFilter(httpTracing);
	}

	@ConditionalOnClass(RibbonCommand.class)
	static class RibbonConfig {
		@Bean
		static TraceRibbonCommandFactoryBeanPostProcessor traceRibbonCommandFactoryBeanPostProcessor(BeanFactory beanFactory) {
			return new TraceRibbonCommandFactoryBeanPostProcessor(beanFactory);
		}
	}

	@Bean
	static TraceZuulHandlerMappingBeanPostProcessor traceHandlerMappingBeanPostProcessor(BeanFactory beanFactory) {
		return new TraceZuulHandlerMappingBeanPostProcessor(beanFactory);
	}

}
