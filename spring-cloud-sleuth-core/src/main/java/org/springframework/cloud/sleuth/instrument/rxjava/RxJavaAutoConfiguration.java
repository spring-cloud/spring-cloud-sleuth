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
<<<<<<< HEAD
 *
 * @author Shivang Shah
=======
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} that
 * enables support for RxJava via {@link RxJavaSchedulersHook}.
 *
 * @author Shivang Shah
 * @since 1.0.0
>>>>>>> e64ea26da45e5f9383d86a9689fe916c53eb7ad2
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
