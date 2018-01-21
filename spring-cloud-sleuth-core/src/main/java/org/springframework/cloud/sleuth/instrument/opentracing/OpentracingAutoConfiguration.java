package org.springframework.cloud.sleuth.instrument.opentracing;

import brave.Tracing;
import brave.opentracing.BraveTracer;
import io.opentracing.Tracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * to enable tracing via Opentracing.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnProperty(value="spring.sleuth.opentracing.enabled", matchIfMissing=true)
@ConditionalOnBean(Tracing.class)
@ConditionalOnClass(Tracer.class)
@EnableConfigurationProperties(SleuthOpentracingProperties.class)
public class OpentracingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Tracer sleuthOpenTracing(brave.Tracing braveTracing) {
		return BraveTracer.create(braveTracing);
	}
}
