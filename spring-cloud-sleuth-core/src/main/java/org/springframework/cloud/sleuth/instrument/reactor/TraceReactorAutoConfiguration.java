package org.springframework.cloud.sleuth.instrument.reactor;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import brave.Tracing;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.sleuth.instrument.async.TraceableScheduledExecutorService;
import org.springframework.cloud.sleuth.instrument.web.TraceWebFluxAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * to enable tracing of Reactor components via Spring Cloud Sleuth.
 *
 * @author Stephane Maldini
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnProperty(value="spring.sleuth.reactor.enabled", matchIfMissing=true)
@ConditionalOnClass(Mono.class)
@AutoConfigureAfter(TraceWebFluxAutoConfiguration.class)
public class TraceReactorAutoConfiguration {

	@Configuration
	@ConditionalOnBean(Tracing.class)
	static class TraceReactorConfiguration {
		@Autowired Tracing tracing;
		@Autowired BeanFactory beanFactory;
		@Autowired LastOperatorWrapper lastOperatorWrapper;

		@Bean
		@ConditionalOnNotWebApplication LastOperatorWrapper spanOperator() {
			return tracer -> Hooks.onLastOperator(ReactorSleuth.spanOperator(tracer));
		}

		@Bean
		@ConditionalOnWebApplication LastOperatorWrapper noOpLastOperatorWrapper() {
			return tracer -> { };
		}

		@PostConstruct
		public void setupHooks() {
			this.lastOperatorWrapper.wrapLastOperator(this.tracing);
			Schedulers.setFactory(new Schedulers.Factory() {
				@Override public ScheduledExecutorService decorateExecutorService(String schedulerType,
						Supplier<? extends ScheduledExecutorService> actual) {
					return new TraceableScheduledExecutorService(
							TraceReactorConfiguration.this.beanFactory,
							actual.get());
				}
			});
		}

		@PreDestroy
		public void cleanupHooks() {
			Hooks.resetOnLastOperator();
			Schedulers.resetFactory();
		}
	}
}

interface LastOperatorWrapper {
	void wrapLastOperator(Tracing tracer);
}