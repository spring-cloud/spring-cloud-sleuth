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
package org.springframework.cloud.sleuth.correlation.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import org.springframework.cloud.sleuth.correlation.CorrelationIdHolder;
import org.springframework.cloud.sleuth.correlation.CorrelationIdUpdater;

import java.util.concurrent.Callable;

/**
 * Abstraction over {@code HystrixCommand} that wraps command execution with CorrelationID setting
 *
 * @see HystrixCommand
 * @see CorrelationIdUpdater
 *
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 */
public abstract class CorrelatedCommand<R> extends HystrixCommand<R> {
	private final String clientCorrelationId = CorrelationIdHolder.get();

	protected CorrelatedCommand(HystrixCommandGroupKey group) {
		super(group);
	}

	protected CorrelatedCommand(Setter setter) {
		super(setter);
	}

	@Override
	protected final R run() throws Exception {
		return CorrelationIdUpdater.withId(clientCorrelationId, new Callable<R>() {
			@Override
			public R call() throws Exception {
				return doRun();
			}

		});
	}

	public abstract R doRun() throws Exception;
}
