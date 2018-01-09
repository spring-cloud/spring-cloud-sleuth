package org.springframework.cloud.brave.instrument.rxjava;

import java.util.Arrays;

import brave.Tracing;
import rx.plugins.RxJavaSchedulersHook;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.brave.TraceKeys;
import org.springframework.cloud.brave.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} that
 * enables support for RxJava via {@link RxJavaSchedulersHook}.
 *
 * @author Shivang Shah
 * @since 1.0.0
 */
@Configuration
@AutoConfigureAfter(TraceAutoConfiguration.class)
@ConditionalOnBean(Tracing.class)
@ConditionalOnClass(RxJavaSchedulersHook.class)
@ConditionalOnProperty(value = "spring.sleuth.rxjava.schedulers.hook.enabled", matchIfMissing = true)
@EnableConfigurationProperties(SleuthRxJavaSchedulersProperties.class)
public class RxJavaAutoConfiguration {

	@Bean
	SleuthRxJavaSchedulersHook sleuthRxJavaSchedulersHook(Tracing tracing, TraceKeys traceKeys,
			SleuthRxJavaSchedulersProperties sleuthRxJavaSchedulersProperties) {
		return new SleuthRxJavaSchedulersHook(tracing, traceKeys,
				Arrays.asList(sleuthRxJavaSchedulersProperties.getIgnoredthreads()));
	}
}
