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

import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Tracer;

/**
 * StepBuilderFactory adding {@link TraceJobExecutionListener}.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceJobBuilderFactory extends JobBuilderFactory {

	private final BeanFactory beanFactory;

	private Tracer tracer;

	public TraceJobBuilderFactory(BeanFactory beanFactory) {
		super(beanFactory.getBean(JobRepository.class));
		this.beanFactory = beanFactory;
	}

	@Override
	public JobBuilder get(String name) {
		return super.get(name).listener(new TraceJobExecutionListener(tracer()));
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

}
