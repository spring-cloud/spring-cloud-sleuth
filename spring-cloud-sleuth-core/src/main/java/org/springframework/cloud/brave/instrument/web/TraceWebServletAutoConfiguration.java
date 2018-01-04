package org.springframework.cloud.brave.instrument.web;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.spring.webmvc.TracingHandlerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.brave.ErrorParser;
import org.springframework.cloud.brave.SpanNamer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
public class TraceWebServletAutoConfiguration implements WebMvcConfigurer {

	@Autowired
	private HttpTracing httpTracing;

	private HandlerInterceptor tracingHandlerInterceptor(HttpTracing httpTracing) {
		return TracingHandlerInterceptor.create(httpTracing);
	}

	@Override public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(tracingHandlerInterceptor(this.httpTracing));
	}

	@Bean
	TraceWebAspect traceWebAspect(Tracing tracing, SpanNamer spanNamer, ErrorParser errorParser) {
		return new TraceWebAspect(tracing, spanNamer, errorParser);
	}
}
