/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.instrument.kafka;

import org.jetbrains.annotations.NotNull;
import reactor.kafka.receiver.KafkaReceiver;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.instrument.kafka.ReactiveKafkaTracingPropagator;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaReceiver;

/**
 * Wraps reactors KafkaReceiver in custom TracingKafkaReceiver that provides tracing
 * context to tracer and reactor context. Downside operators will keep the context correct
 * thanks to
 * {@link org.springframework.cloud.sleuth.autoconfig.instrument.reactor.TraceReactorAutoConfiguration}
 */
public class TracingKafkaReceiverBeanPostProcessor implements BeanPostProcessor {

	private final ReactiveKafkaTracingPropagator reactiveKafkaTracingPropagator;

	public TracingKafkaReceiverBeanPostProcessor(ReactiveKafkaTracingPropagator reactiveKafkaTracingPropagator) {
		this.reactiveKafkaTracingPropagator = reactiveKafkaTracingPropagator;
	}

	@Override
	public Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
		if (bean instanceof KafkaReceiver && !(bean instanceof TracingKafkaReceiver)) {
			return new TracingKafkaReceiver<>(reactiveKafkaTracingPropagator, (KafkaReceiver<?, ?>) bean);
		}

		return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
	}

}
