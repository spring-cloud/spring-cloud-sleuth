package org.springframework.cloud.sleuth.instrument.rxjava;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import rx.plugins.RxJavaSchedulersHook;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} that
 * enables support for RxJava via {@link RxJavaSchedulersHook}.
 *
 * @author Shivang Shah
 * @since 1.0.0
 */
@Configuration
@AutoConfigureAfter(TraceAutoConfiguration.class)
@ConditionalOnBean(Tracer.class)
@ConditionalOnClass(RxJavaSchedulersHook.class)
@ConditionalOnProperty(value = "spring.sleuth.rxjava.schedulers.hook.enabled", matchIfMissing = true)
public class RxJavaAutoConfiguration {

	/**
	 * Contains a list of thread names for which spans will not be sampled. Extracted to a constant
	 * for readability reasons.
	 */
	private static final List<String> DEFAULT_IGNORED_THREADS = Arrays.asList("HystrixMetricPoller", "^RxComputation.*$");

	@Bean
	SleuthRxJavaSchedulersHook sleuthRxJavaSchedulersHook(Tracer tracer, TraceKeys traceKeys,
			// Comma separated list of thread name matchers
			@Value("${spring.sleuth.rxjava.schedulers.ignoredthreads:}") String threadsToSample) {
		return new SleuthRxJavaSchedulersHook(tracer, traceKeys, threads(threadsToSample));
	}

	private List<String> threads(String threadsToSample) {
		List<String> threads = new ArrayList<>();
		if (StringUtils.isEmpty(threadsToSample)) {
			threads.addAll(DEFAULT_IGNORED_THREADS);
		} else {
			threads.addAll(Arrays.asList(threadsToSample.split(",")));
		}
		return threads;
	}
}
