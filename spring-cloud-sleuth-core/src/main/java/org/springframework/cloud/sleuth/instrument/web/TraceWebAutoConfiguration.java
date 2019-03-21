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
package org.springframework.cloud.sleuth.instrument.web;

import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import java.util.ArrayList;
import java.util.List;

import static javax.servlet.DispatcherType.ASYNC;
import static javax.servlet.DispatcherType.ERROR;
import static javax.servlet.DispatcherType.FORWARD;
import static javax.servlet.DispatcherType.INCLUDE;
import static javax.servlet.DispatcherType.REQUEST;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables tracing to HTTP requests.
 *
 * @author Tomasz Nurkewicz, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @since 1.0.0
 */
@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@ConditionalOnProperty(value = "spring.sleuth.web.enabled", matchIfMissing = true)
@ConditionalOnWebApplication
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(TraceHttpAutoConfiguration.class)
public class TraceWebAutoConfiguration {

	/**
	 * Nested config that configures Web MVC if it's present (without adding a runtime
	 * dependency to it)
	 */
	@Configuration
	@ConditionalOnClass(WebMvcConfigurerAdapter.class)
	@Import(TraceWebMvcConfigurer.class)
	protected static class TraceWebMvcAutoConfiguration {
	}

	@Bean
	public TraceWebAspect traceWebAspect(Tracer tracer, TraceKeys traceKeys,
			SpanNamer spanNamer, ErrorParser errorParser) {
		return new TraceWebAspect(tracer, spanNamer, traceKeys, errorParser);
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.data.rest.webmvc.support.DelegatingHandlerMapping")
	public TraceSpringDataBeanPostProcessor traceSpringDataBeanPostProcessor(
			BeanFactory beanFactory) {
		return new TraceSpringDataBeanPostProcessor(beanFactory);
	}

	@Bean
	public FilterRegistrationBean traceWebFilter(TraceFilter traceFilter, SleuthWebProperties sleuthWebProperties) {
		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(
				traceFilter);
		filterRegistrationBean.setDispatcherTypes(ASYNC, ERROR, FORWARD, INCLUDE,
				REQUEST);
		filterRegistrationBean.setOrder(sleuthWebProperties.getFilterOrder());
		return filterRegistrationBean;
	}

	@Bean
	@ConditionalOnMissingBean
	public TraceFilter traceFilter(BeanFactory beanFactory,
			SkipPatternProvider skipPatternProvider) {
		return new TraceFilter(beanFactory, skipPatternProvider.skipPattern());
	}


	@Configuration
	@ConditionalOnClass(name = "org.apache.catalina.connector.ClientAbortException")
	protected static class ClientAbortExceptionToIgnoreInTraceFilterConfig{
		/**
		 * Ignore the name of {@link ClientAbortException} when use tomcat. Because the tomcat will ignore this exception
		 * in {@link org.apache.catalina.core.StandardHostValve#throwable(Request, Response, Throwable)}, Causes the current span to not close.
		 * More detail see #1038.
		 */
		@Bean
		public ExceptionToIgnoreInTraceFilter clientAbortExceptionToIgnoreInTraceFilter(){
			return new ExceptionToIgnoreInTraceFilter() {
				@Override
				public String exceptionClassName() {
					return ClientAbortException.class.getName();
				}
			};
		}
	}

	@Autowired(required=false)
	List<ExceptionToIgnoreInTraceFilter> exceptionsToIgnoreInTraceFilter = new ArrayList<>();

	@Bean
	ExceptionToIgnoreInTraceFilterProvider exceptionToIgnoreInTraceFilterProvider() {
		return new ExceptionToIgnoreInTraceFilterProvider(this.exceptionsToIgnoreInTraceFilter);
	}

}
