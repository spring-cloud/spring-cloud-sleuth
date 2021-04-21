/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.batch;

import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * StepBuilderFactory adding {@link TraceStepExecutionListener}.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceStepBuilderFactory extends StepBuilderFactory {

	private final BeanFactory beanFactory;

	private final StepBuilderFactory delegate;

	private Tracer tracer;

	public TraceStepBuilderFactory(BeanFactory beanFactory, StepBuilderFactory delegate) {
		super(beanFactory.getBean(JobRepository.class), beanFactory.getBean(PlatformTransactionManager.class));
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override
	public StepBuilder get(String name) {
		return this.delegate.get(name).listener(new TraceStepExecutionListener(tracer()));
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

}
