package org.springframework.cloud.sleuth.instrument.rxjava;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rx.plugins.RxJavaSchedulersHook;

/**
 *
 * @author Shivang Shah
 */
@Configuration
@AutoConfigureAfter(TraceAutoConfiguration.class)
@ConditionalOnBean(Tracer.class)
@ConditionalOnClass(RxJavaSchedulersHook.class)
@ConditionalOnProperty(value = "spring.sleuth.rxjava.schedulers.hook.enabled", matchIfMissing = true)
public class RxJavaAutoConfiguration {

	@Bean
	SleuthRxJavaSchedulersHook sleuthRxJavaSchedulersHook(Tracer tracer, TraceKeys traceKeys) {
		return new SleuthRxJavaSchedulersHook(tracer, traceKeys);
	}
}
