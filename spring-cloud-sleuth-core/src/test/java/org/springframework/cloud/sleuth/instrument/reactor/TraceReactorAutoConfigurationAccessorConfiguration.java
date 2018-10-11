package org.springframework.cloud.sleuth.instrument.reactor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Hooks;
import reactor.core.scheduler.Schedulers;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Marcin Grzejszczak
 */
@Configuration
@AutoConfigureBefore(TraceReactorAutoConfiguration.class)
public class TraceReactorAutoConfigurationAccessorConfiguration {

	private static final Log log = LogFactory
			.getLog(TraceReactorAutoConfigurationAccessorConfiguration.class);

	public static void close() {
		if (log.isTraceEnabled()) {
			log.trace("Cleaning up hooks");
		}
		new TraceReactorAutoConfiguration.TraceReactorConfiguration().cleanupHooks();
		Hooks.resetOnEachOperator();
		Hooks.resetOnLastOperator();
		Schedulers.resetFactory();
	}

	@Bean
	static HookRegisteringBeanDefinitionRegistryPostProcessor testTraceHookRegisteringBeanDefinitionRegistryPostProcessor(
			ConfigurableApplicationContext context) {
		log.info("Running clean up and creating the post processor");
		close();
		return TraceReactorAutoConfiguration.TraceReactorConfiguration
				.traceHookRegisteringBeanDefinitionRegistryPostProcessor(context);
	}

}