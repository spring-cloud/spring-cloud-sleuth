/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.sleuth.correlation.scheduling;

import org.springframework.cloud.sleuth.correlation.UuidGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Registers beans related to task scheduling.
 *
 * @see ScheduledTaskWithCorrelationIdAspect
 *
 * @author Michal Chmielarz, 4financeIT
 */
@Configuration
@EnableScheduling
@EnableAspectJAutoProxy
public class TaskSchedulingConfiguration {
	@Bean
	public ScheduledTaskWithCorrelationIdAspect scheduledTaskPointcut(UuidGenerator uuidGenerator) {
		return new ScheduledTaskWithCorrelationIdAspect(uuidGenerator);
	}

}
