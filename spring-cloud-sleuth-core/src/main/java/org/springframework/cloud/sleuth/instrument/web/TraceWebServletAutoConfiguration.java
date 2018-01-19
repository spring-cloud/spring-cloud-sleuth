package org.springframework.cloud.sleuth.instrument.web;

import brave.Tracer;
import brave.http.HttpTracing;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static javax.servlet.DispatcherType.ASYNC;
import static javax.servlet.DispatcherType.ERROR;
import static javax.servlet.DispatcherType.FORWARD;
import static javax.servlet.DispatcherType.INCLUDE;
import static javax.servlet.DispatcherType.REQUEST;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables tracing to HTTP requests.
 *
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.web.enabled", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(HttpTracing.class)
@AutoConfigureAfter(TraceHttpAutoConfiguration.class)
public class TraceWebServletAutoConfiguration {

	/**
	 * Nested config that configures Web MVC if it's present (without adding a runtime
	 * dependency to it)
	 */
	@Configuration
	@ConditionalOnClass(WebMvcConfigurer.class)
	@Import(TraceWebMvcConfigurer.class)
	protected static class TraceWebMvcAutoConfiguration {
	}

	@Bean
	TraceWebAspect traceWebAspect(Tracer tracer, SpanNamer spanNamer, ErrorParser errorParser) {
		return new TraceWebAspect(tracer, spanNamer, errorParser);
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.data.rest.webmvc.support.DelegatingHandlerMapping")
	public TraceSpringDataBeanPostProcessor traceSpringDataBeanPostProcessor(
			BeanFactory beanFactory) {
		return new TraceSpringDataBeanPostProcessor(beanFactory);
	}
	
	@Bean
	public FilterRegistrationBean traceWebFilter(
			TraceFilter traceFilter) {
		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(traceFilter);
		filterRegistrationBean.setDispatcherTypes(ASYNC, ERROR, FORWARD, INCLUDE, REQUEST);
		filterRegistrationBean.setOrder(TraceFilter.ORDER);
		return filterRegistrationBean;
	}

	@Bean
	@ConditionalOnMissingBean
	public TraceFilter traceFilter(BeanFactory beanFactory,
			SkipPatternProvider skipPatternProvider) {
		return new TraceFilter(beanFactory, skipPatternProvider.skipPattern());
	}
}
